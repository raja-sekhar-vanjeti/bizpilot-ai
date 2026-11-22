package com.bizpilot.dashboard.service;

import com.bizpilot.customer.repository.CustomerRepository;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.repository.ExpenseRepository;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int RECENT_PER_TYPE = 10;
    private static final int CHART_MONTHS = 12;

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;

    public DashboardService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository,
            CustomerRepository customerRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.customerRepository = customerRepository;
    }

    public record DashboardSummary(
            BigDecimal totalInvoiced,
            BigDecimal totalCollected,
            BigDecimal outstandingReceivables,
            BigDecimal overdueReceivables,
            BigDecimal totalExpenses,
            BigDecimal netCashMovement,
            long customerCount,
            long invoiceCount,
            long paidInvoiceCount,
            long pendingInvoiceCount,
            long overdueInvoiceCount) {
    }

    public record TransactionView(String type, String reference, BigDecimal amount, LocalDate date) {
    }

    public record MonthlyPoint(String month, BigDecimal revenue, BigDecimal expenses) {
    }

    // Invoiced totals (what was billed) and collected cash (what was actually received) are
    // separate measures. Invoice totals are never counted as cash.
    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID ownerId) {
        LocalDate today = LocalDate.now();

        List<Invoice> issued = invoiceRepository.findByOwnerIdAndStatusNotIn(
                ownerId, List.of(InvoiceStatus.CANCELLED, InvoiceStatus.DRAFT));
        List<Payment> payments = paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId);
        List<Expense> expenses = expenseRepository.findByOwnerId(ownerId, Pageable.unpaged()).getContent();

        BigDecimal totalInvoiced = sum(issued.stream().map(Invoice::getTotalAmount));
        BigDecimal totalCollected = sum(payments.stream().map(Payment::getAmount));
        BigDecimal totalExpenses = sum(expenses.stream().map(Expense::getAmount));

        BigDecimal outstandingReceivables = sum(issued.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID)
                .map(Invoice::getOutstandingAmount));

        List<Invoice> overdue = issued.stream().filter(i -> isOverdue(i, today)).toList();
        BigDecimal overdueReceivables = sum(overdue.stream().map(Invoice::getOutstandingAmount));

        long paidInvoiceCount = issued.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).count();
        long pendingInvoiceCount = issued.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID && !isOverdue(i, today))
                .count();

        BigDecimal netCashMovement = totalCollected.subtract(totalExpenses).setScale(2, RoundingMode.HALF_UP);

        long customerCount = customerRepository
                .findByOwnerIdAndArchivedFalse(ownerId, Pageable.unpaged())
                .getTotalElements();
        long invoiceCount = invoiceRepository.countByOwnerId(ownerId);

        return new DashboardSummary(
                totalInvoiced,
                totalCollected,
                outstandingReceivables,
                overdueReceivables,
                totalExpenses,
                netCashMovement,
                customerCount,
                invoiceCount,
                paidInvoiceCount,
                pendingInvoiceCount,
                overdue.size());
    }

    @Transactional(readOnly = true)
    public List<TransactionView> recentTransactions(UUID ownerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Stream<TransactionView> payments = paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId).stream()
                .limit(RECENT_PER_TYPE)
                .map(p -> new TransactionView(
                        "PAYMENT",
                        p.getReference() == null ? "" : p.getReference(),
                        p.getAmount(),
                        p.getPaymentDate()));

        Stream<TransactionView> expenses = expenseRepository.findByOwnerId(ownerId, Pageable.unpaged()).getContent().stream()
                .sorted(Comparator.comparing(Expense::getExpenseDate).reversed())
                .limit(RECENT_PER_TYPE)
                .map(e -> new TransactionView(
                        "EXPENSE",
                        e.getDescription(),
                        e.getAmount(),
                        e.getExpenseDate()));

        return Stream.concat(payments, expenses)
                .sorted(Comparator.comparing(TransactionView::date).reversed())
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthlyPoint> monthlyChart(UUID ownerId) {
        YearMonth first = YearMonth.now().minusMonths(CHART_MONTHS - 1);

        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> expensesByMonth = new LinkedHashMap<>();
        for (int i = 0; i < CHART_MONTHS; i++) {
            YearMonth month = first.plusMonths(i);
            revenueByMonth.put(month, BigDecimal.ZERO);
            expensesByMonth.put(month, BigDecimal.ZERO);
        }

        for (Payment payment : paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)) {
            revenueByMonth.computeIfPresent(
                    YearMonth.from(payment.getPaymentDate()),
                    (month, total) -> total.add(payment.getAmount()));
        }
        for (Expense expense : expenseRepository.findByOwnerId(ownerId, Pageable.unpaged()).getContent()) {
            expensesByMonth.computeIfPresent(
                    YearMonth.from(expense.getExpenseDate()),
                    (month, total) -> total.add(expense.getAmount()));
        }

        List<MonthlyPoint> points = new ArrayList<>();
        for (YearMonth month : revenueByMonth.keySet()) {
            points.add(new MonthlyPoint(
                    month.format(MONTH_FORMAT),
                    revenueByMonth.get(month).setScale(2, RoundingMode.HALF_UP),
                    expensesByMonth.get(month).setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    private static boolean isOverdue(Invoice invoice, LocalDate today) {
        InvoiceStatus status = invoice.getStatus();
        if (status == InvoiceStatus.OVERDUE) {
            return true;
        }
        return (status == InvoiceStatus.SENT || status == InvoiceStatus.PARTIALLY_PAID)
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(today);
    }

    private static BigDecimal sum(Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
