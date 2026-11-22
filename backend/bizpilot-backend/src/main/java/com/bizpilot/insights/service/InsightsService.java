package com.bizpilot.insights.service;

import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.dto.ForecastGranularity;
import com.bizpilot.cashflow.service.CashflowService;
import com.bizpilot.expense.repository.ExpenseRepository;
import com.bizpilot.insights.dto.InsightResponse;
import com.bizpilot.insights.dto.InsightSeverity;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic, rule-based financial insights. No external services are called; every rule is a
 * fixed threshold over the authenticated business's own data.
 */
@Service
public class InsightsService {

    static final int SEVERE_OVERDUE_DAYS = 30;
    static final BigDecimal HIGH_OUTSTANDING_RATIO = new BigDecimal("0.50");
    static final int LATE_PAYMENT_THRESHOLD = 2;
    static final BigDecimal EXPENSE_SPIKE_RATIO = new BigDecimal("1.25");
    static final BigDecimal TREND_CHANGE_RATIO = new BigDecimal("0.20");
    static final int UPCOMING_DAYS = 7;
    static final int WINDOW_DAYS = 30;

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CashflowService cashflowService;
    private final Clock clock;

    public InsightsService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository,
            CashflowService cashflowService,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.cashflowService = cashflowService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InsightResponse> insights(UUID ownerId) {
        LocalDate today = LocalDate.now(clock);
        List<Invoice> issued = invoiceRepository.findByOwnerIdAndStatusNotIn(
                ownerId, List.of(InvoiceStatus.DRAFT, InvoiceStatus.CANCELLED));
        List<Invoice> open = issued.stream().filter(InsightsService::isOpen).toList();

        List<InsightResponse> insights = new ArrayList<>();
        overdueInvoices(open, today, insights);
        highOutstanding(issued, open, insights);
        repeatedLatePayers(ownerId, issued, open, today, insights);
        expensesAndCollections(ownerId, today, insights);
        lowProjectedCash(ownerId, insights);
        upcomingDue(open, today, insights);

        insights.sort(Comparator.comparing(InsightResponse::severity)
                .thenComparing(InsightResponse::amount, Comparator.nullsLast(Comparator.reverseOrder())));
        return insights;
    }

    private void overdueInvoices(List<Invoice> open, LocalDate today, List<InsightResponse> out) {
        List<Invoice> overdue = open.stream().filter(i -> i.getDueDate().isBefore(today)).toList();
        if (overdue.isEmpty()) {
            return;
        }
        BigDecimal amount = sumOutstanding(overdue);
        LocalDate oldest = overdue.stream().map(Invoice::getDueDate).min(LocalDate::compareTo).orElseThrow();
        boolean severe = oldest.isBefore(today.minusDays(SEVERE_OVERDUE_DAYS));
        out.add(new InsightResponse(
                "OVERDUE_INVOICES",
                "Overdue invoices",
                overdue.size() + " invoice(s) are past their due date with " + amount.toPlainString()
                        + " outstanding. The oldest was due on " + oldest + ".",
                severe ? InsightSeverity.CRITICAL : InsightSeverity.WARNING,
                amount,
                "Follow up with these customers and generate payment reminders.",
                oldest));
    }

    private void highOutstanding(List<Invoice> issued, List<Invoice> open, List<InsightResponse> out) {
        BigDecimal outstanding = sumOutstanding(open);
        BigDecimal invoiced = money(issued.stream().map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (outstanding.signum() <= 0 || invoiced.signum() <= 0) {
            return;
        }
        BigDecimal ratio = outstanding.divide(invoiced, 4, MONEY_ROUNDING);
        if (ratio.compareTo(HIGH_OUTSTANDING_RATIO) < 0) {
            return;
        }
        String percent = ratio.multiply(BigDecimal.valueOf(100)).setScale(0, MONEY_ROUNDING).toPlainString();
        out.add(new InsightResponse(
                "HIGH_OUTSTANDING_RECEIVABLES",
                "High outstanding receivables",
                percent + "% of the amount invoiced (" + outstanding.toPlainString() + " of "
                        + invoiced.toPlainString() + ") has not been collected yet.",
                InsightSeverity.WARNING,
                outstanding,
                "Prioritise collections and consider shorter payment terms or advance payments.",
                null));
    }

    private void repeatedLatePayers(UUID ownerId, List<Invoice> issued, List<Invoice> open, LocalDate today,
                                    List<InsightResponse> out) {
        Map<UUID, LocalDate> lastPaymentByInvoice = new HashMap<>();
        for (Payment payment : paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)) {
            lastPaymentByInvoice.merge(payment.getInvoice().getId(), payment.getPaymentDate(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }

        Map<UUID, Integer> lateCount = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (Invoice invoice : issued) {
            boolean late;
            if (invoice.getStatus() == InvoiceStatus.PAID) {
                LocalDate lastPayment = lastPaymentByInvoice.get(invoice.getId());
                late = lastPayment != null && lastPayment.isAfter(invoice.getDueDate());
            } else {
                late = isOpen(invoice) && invoice.getDueDate().isBefore(today);
            }
            if (late) {
                UUID customerId = invoice.getCustomer().getId();
                lateCount.merge(customerId, 1, Integer::sum);
                names.put(customerId, invoice.getCustomer().getName());
            }
        }

        lateCount.forEach((customerId, count) -> {
            if (count < LATE_PAYMENT_THRESHOLD) {
                return;
            }
            BigDecimal customerOutstanding = sumOutstanding(open.stream()
                    .filter(i -> customerId.equals(i.getCustomer().getId())).toList());
            out.add(new InsightResponse(
                    "REPEATED_LATE_PAYER",
                    "Repeated late payments: " + names.get(customerId),
                    names.get(customerId) + " paid late or is overdue on " + count + " invoices.",
                    InsightSeverity.WARNING,
                    customerOutstanding,
                    "Request advance or milestone payments from this customer and send reminders before due dates.",
                    null));
        });
    }

    private void expensesAndCollections(UUID ownerId, LocalDate today, List<InsightResponse> out) {
        LocalDate windowStart = today.minusDays(WINDOW_DAYS - 1L);
        LocalDate previousStart = windowStart.minusDays(WINDOW_DAYS);
        LocalDate previousEnd = windowStart.minusDays(1);

        BigDecimal recentExpenses = money(expenseRepository.sumAmountByOwnerIdBetween(ownerId, windowStart, today));
        BigDecimal baselineExpenses = money(expenseRepository.sumAmountByOwnerIdBetween(
                ownerId, windowStart.minusDays(3L * WINDOW_DAYS), previousEnd));
        BigDecimal baselineMonthly = baselineExpenses.divide(BigDecimal.valueOf(3), MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal recentCollections = money(paymentRepository.sumAmountByOwnerIdBetween(ownerId, windowStart, today));
        BigDecimal previousCollections = money(paymentRepository.sumAmountByOwnerIdBetween(
                ownerId, previousStart, previousEnd));

        if (baselineMonthly.signum() > 0
                && recentExpenses.compareTo(baselineMonthly.multiply(EXPENSE_SPIKE_RATIO)) > 0) {
            out.add(new InsightResponse(
                    "HIGH_MONTHLY_EXPENSES",
                    "Expenses are higher than usual",
                    "Expenses in the last " + WINDOW_DAYS + " days were " + recentExpenses.toPlainString()
                            + ", against a monthly average of " + baselineMonthly.toPlainString()
                            + " over the previous three months.",
                    InsightSeverity.WARNING,
                    recentExpenses,
                    "Review recent expenses by category and postpone non-essential spending.",
                    today));
        }

        if (recentExpenses.signum() > 0 && recentExpenses.compareTo(recentCollections) > 0) {
            BigDecimal gap = recentExpenses.subtract(recentCollections);
            out.add(new InsightResponse(
                    "EXPENSES_EXCEED_COLLECTIONS",
                    "Spending more than collected",
                    "In the last " + WINDOW_DAYS + " days expenses (" + recentExpenses.toPlainString()
                            + ") exceeded payments received (" + recentCollections.toPlainString() + ").",
                    InsightSeverity.WARNING,
                    gap,
                    "Speed up collections on open invoices or reduce discretionary expenses.",
                    today));
        }

        if (previousCollections.signum() > 0) {
            BigDecimal change = recentCollections.subtract(previousCollections)
                    .divide(previousCollections, 4, MONEY_ROUNDING);
            String percent = change.abs().multiply(BigDecimal.valueOf(100)).setScale(0, MONEY_ROUNDING).toPlainString();
            if (change.compareTo(TREND_CHANGE_RATIO.negate()) <= 0) {
                out.add(new InsightResponse(
                        "COLLECTIONS_DECLINING",
                        "Collections are down",
                        "Payments received in the last " + WINDOW_DAYS + " days (" + recentCollections.toPlainString()
                                + ") are " + percent + "% lower than the previous " + WINDOW_DAYS + " days ("
                                + previousCollections.toPlainString() + ").",
                        InsightSeverity.WARNING,
                        recentCollections,
                        "Check for delayed customer payments and follow up on open invoices.",
                        today));
            } else if (change.compareTo(TREND_CHANGE_RATIO) >= 0) {
                out.add(new InsightResponse(
                        "COLLECTIONS_GROWING",
                        "Collections are up",
                        "Payments received in the last " + WINDOW_DAYS + " days (" + recentCollections.toPlainString()
                                + ") are " + percent + "% higher than the previous " + WINDOW_DAYS + " days ("
                                + previousCollections.toPlainString() + ").",
                        InsightSeverity.INFO,
                        recentCollections,
                        "Consider setting aside part of the surplus as a cash reserve.",
                        today));
            }
        }
    }

    private void lowProjectedCash(UUID ownerId, List<InsightResponse> out) {
        CashflowForecastResponse forecast = cashflowService.forecast(ownerId, WINDOW_DAYS, ForecastGranularity.WEEKLY);
        BigDecimal lowest = forecast.lowestProjectedBalance();
        BigDecimal monthlyBurn = forecast.averageDailyExpense().multiply(BigDecimal.valueOf(WINDOW_DAYS));
        InsightSeverity severity;
        if (lowest.signum() < 0) {
            severity = InsightSeverity.CRITICAL;
        } else if (monthlyBurn.signum() > 0 && lowest.compareTo(monthlyBurn) < 0) {
            severity = InsightSeverity.WARNING;
        } else {
            return;
        }
        out.add(new InsightResponse(
                "LOW_PROJECTED_CASH",
                severity == InsightSeverity.CRITICAL ? "Projected cash shortfall" : "Low projected cash balance",
                "The " + WINDOW_DAYS + "-day forecast reaches a lowest balance of " + lowest.toPlainString()
                        + " on " + forecast.lowestBalanceDate() + ", while typical monthly expenses are about "
                        + money(monthlyBurn).toPlainString() + ".",
                severity,
                lowest,
                "Collect overdue invoices early, delay large purchases and keep a cash buffer.",
                forecast.lowestBalanceDate()));
    }

    private void upcomingDue(List<Invoice> open, LocalDate today, List<InsightResponse> out) {
        LocalDate limit = today.plusDays(UPCOMING_DAYS);
        List<Invoice> upcoming = open.stream()
                .filter(i -> !i.getDueDate().isBefore(today) && !i.getDueDate().isAfter(limit))
                .toList();
        if (upcoming.isEmpty()) {
            return;
        }
        BigDecimal amount = sumOutstanding(upcoming);
        LocalDate earliest = upcoming.stream().map(Invoice::getDueDate).min(LocalDate::compareTo).orElseThrow();
        out.add(new InsightResponse(
                "UPCOMING_DUE_INVOICES",
                "Invoices due soon",
                upcoming.size() + " invoice(s) worth " + amount.toPlainString() + " are due in the next "
                        + UPCOMING_DAYS + " days.",
                InsightSeverity.INFO,
                amount,
                "Send a friendly reminder before the due date.",
                earliest));
    }

    private static boolean isOpen(Invoice invoice) {
        InvoiceStatus status = invoice.getStatus();
        return (status == InvoiceStatus.SENT || status == InvoiceStatus.PARTIALLY_PAID || status == InvoiceStatus.OVERDUE)
                && invoice.getOutstandingAmount() != null
                && invoice.getOutstandingAmount().signum() > 0;
    }

    private static BigDecimal sumOutstanding(List<Invoice> invoices) {
        return money(invoices.stream().map(Invoice::getOutstandingAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
