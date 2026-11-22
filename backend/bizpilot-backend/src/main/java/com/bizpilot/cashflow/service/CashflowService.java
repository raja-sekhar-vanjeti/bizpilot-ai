package com.bizpilot.cashflow.service;

import com.bizpilot.cashflow.dto.AffectedInvoice;
import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.dto.ExpectedInvoice;
import com.bizpilot.cashflow.dto.ForecastGranularity;
import com.bizpilot.cashflow.dto.ForecastPeriod;
import com.bizpilot.cashflow.dto.ScenarioSummary;
import com.bizpilot.cashflow.dto.WhatIfRequest;
import com.bizpilot.cashflow.dto.WhatIfResponse;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash-flow forecasting. Everything here is read-only and scoped to the owner id taken from the
 * authenticated principal.
 *
 * <ul>
 *   <li>Opening balance = payments received up to today minus expenses recorded up to today.</li>
 *   <li>Confirmed inflows = payments already recorded with a future payment date.</li>
 *   <li>Expected inflows = outstanding balances of SENT / PARTIALLY_PAID / OVERDUE invoices on
 *       their due date (overdue balances are expected on the first forecast day).</li>
 *   <li>Scheduled outflows = expenses already recorded with a future date.</li>
 *   <li>Projected outflows = average daily expense of the last 90 days, for every forecast day.</li>
 * </ul>
 */
@Service
public class CashflowService {

    public static final Set<Integer> ALLOWED_HORIZONS = Set.of(30, 60, 90);
    static final int EXPENSE_HISTORY_DAYS = 90;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final List<InvoiceStatus> NOT_COLLECTABLE =
            List.of(InvoiceStatus.DRAFT, InvoiceStatus.CANCELLED, InvoiceStatus.PAID);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public CashflowService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository,
            CustomerRepository customerRepository,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CashflowForecastResponse forecast(UUID ownerId, int days, ForecastGranularity granularity) {
        validateHorizon(days);
        Inputs inputs = loadInputs(ownerId, days);
        return compute(inputs, Map.of(), granularity == null ? ForecastGranularity.WEEKLY : granularity);
    }

    @Transactional(readOnly = true)
    public WhatIfResponse whatIf(UUID ownerId, WhatIfRequest request) {
        int days = request.days() == null ? 30 : request.days();
        validateHorizon(days);
        int delay = request.delayDays();
        Inputs inputs = loadInputs(ownerId, days);

        List<ExpectedInvoice> affected = selectAffected(ownerId, inputs.invoices(), request);
        Map<UUID, LocalDate> overrides = new HashMap<>();
        for (ExpectedInvoice invoice : affected) {
            overrides.put(invoice.invoiceId(), invoice.expectedDate().plusDays(delay));
        }

        CashflowForecastResponse baseline = compute(inputs, Map.of(), ForecastGranularity.WEEKLY);
        CashflowForecastResponse simulated = compute(inputs, overrides, ForecastGranularity.WEEKLY);

        BigDecimal pushedBeyond = BigDecimal.ZERO;
        List<AffectedInvoice> affectedViews = new ArrayList<>();
        for (ExpectedInvoice invoice : affected) {
            LocalDate revised = overrides.get(invoice.invoiceId());
            boolean wasInside = !invoice.expectedDate().isAfter(inputs.end());
            boolean nowOutside = revised.isAfter(inputs.end());
            if (wasInside && nowOutside) {
                pushedBeyond = pushedBeyond.add(invoice.outstandingAmount());
            }
            affectedViews.add(new AffectedInvoice(
                    invoice.invoiceId(),
                    invoice.invoiceNumber(),
                    invoice.customerName(),
                    invoice.outstandingAmount(),
                    invoice.expectedDate(),
                    revised,
                    wasInside && nowOutside));
        }

        return new WhatIfResponse(
                days,
                delay,
                summaryOf(baseline),
                summaryOf(simulated),
                simulated.projectedClosingBalance().subtract(baseline.projectedClosingBalance()),
                simulated.lowestProjectedBalance().subtract(baseline.lowestProjectedBalance()),
                money(pushedBeyond),
                affectedViews,
                baseline.periods(),
                simulated.periods());
    }

    private List<ExpectedInvoice> selectAffected(UUID ownerId, List<ExpectedInvoice> open, WhatIfRequest request) {
        if (request.customerId() != null) {
            customerRepository.findByIdAndOwnerId(request.customerId(), ownerId)
                    .orElseThrow(() -> new NotFoundException("Customer not found"));
        }
        if (request.invoiceIds() != null && !request.invoiceIds().isEmpty()) {
            Map<UUID, ExpectedInvoice> byId = new HashMap<>();
            open.forEach(invoice -> byId.put(invoice.invoiceId(), invoice));
            List<ExpectedInvoice> selected = new ArrayList<>();
            for (UUID id : new LinkedHashSet<>(request.invoiceIds())) {
                ExpectedInvoice invoice = byId.get(id);
                if (invoice == null
                        || (request.customerId() != null && !request.customerId().equals(invoice.customerId()))) {
                    throw new NotFoundException("Open invoice not found: " + id);
                }
                selected.add(invoice);
            }
            return selected;
        }
        if (request.customerId() != null) {
            return open.stream().filter(i -> request.customerId().equals(i.customerId())).toList();
        }
        return open;
    }

    private Inputs loadInputs(UUID ownerId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.plusDays(1);
        LocalDate end = today.plusDays(days);

        BigDecimal cashIn = money(paymentRepository.sumAmountByOwnerIdUpTo(ownerId, today));
        BigDecimal cashOut = money(expenseRepository.sumAmountByOwnerIdUpTo(ownerId, today));

        BigDecimal history = money(expenseRepository.sumAmountByOwnerIdBetween(
                ownerId, today.minusDays(EXPENSE_HISTORY_DAYS - 1L), today));
        BigDecimal averageDaily = history.divide(BigDecimal.valueOf(EXPENSE_HISTORY_DAYS), MONEY_SCALE, MONEY_ROUNDING);

        List<Payment> futurePayments = paymentRepository.findByOwnerIdAndPaymentDateBetween(ownerId, start, end);
        List<Expense> futureExpenses =
                expenseRepository.findByOwnerIdAndExpenseDateBetweenOrderByExpenseDateAsc(ownerId, start, end);

        List<ExpectedInvoice> invoices = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByOwnerIdAndStatusNotIn(ownerId, NOT_COLLECTABLE)) {
            BigDecimal outstanding = invoice.getOutstandingAmount();
            if (outstanding == null || outstanding.signum() <= 0) {
                continue;
            }
            LocalDate due = invoice.getDueDate();
            LocalDate expected = due.isBefore(start) ? start : due;
            invoices.add(new ExpectedInvoice(
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getCustomer().getId(),
                    invoice.getCustomer().getName(),
                    money(outstanding),
                    due,
                    expected,
                    due.isBefore(today),
                    !expected.isAfter(end)));
        }
        invoices.sort(Comparator.comparing(ExpectedInvoice::expectedDate).thenComparing(ExpectedInvoice::invoiceNumber));

        return new Inputs(days, today, start, end, cashIn, cashOut, averageDaily, futurePayments, futureExpenses, invoices);
    }

    private CashflowForecastResponse compute(Inputs in, Map<UUID, LocalDate> overrides, ForecastGranularity granularity) {
        Map<LocalDate, BigDecimal> confirmed = new HashMap<>();
        for (Payment payment : in.futurePayments()) {
            confirmed.merge(payment.getPaymentDate(), payment.getAmount(), BigDecimal::add);
        }
        Map<LocalDate, BigDecimal> scheduled = new HashMap<>();
        for (Expense expense : in.futureExpenses()) {
            scheduled.merge(expense.getExpenseDate(), expense.getAmount(), BigDecimal::add);
        }
        Map<LocalDate, BigDecimal> expected = new HashMap<>();
        BigDecimal beyondHorizon = BigDecimal.ZERO;
        for (ExpectedInvoice invoice : in.invoices()) {
            LocalDate date = overrides.getOrDefault(invoice.invoiceId(), invoice.expectedDate());
            if (date.isAfter(in.end())) {
                beyondHorizon = beyondHorizon.add(invoice.outstandingAmount());
            } else {
                expected.merge(date, invoice.outstandingAmount(), BigDecimal::add);
            }
        }

        BigDecimal opening = in.cashIn().subtract(in.cashOut());
        int step = granularity == ForecastGranularity.DAILY ? 1 : 7;

        List<ForecastPeriod> periods = new ArrayList<>();
        BigDecimal balance = opening;
        BigDecimal lowest = null;
        LocalDate lowestDate = null;
        BigDecimal totalConfirmed = BigDecimal.ZERO;
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalScheduled = BigDecimal.ZERO;
        BigDecimal totalProjected = BigDecimal.ZERO;

        LocalDate periodStart = in.start();
        while (!periodStart.isAfter(in.end())) {
            LocalDate periodEnd = periodStart.plusDays(step - 1L);
            if (periodEnd.isAfter(in.end())) {
                periodEnd = in.end();
            }
            BigDecimal periodOpening = balance;
            BigDecimal pConfirmed = BigDecimal.ZERO;
            BigDecimal pExpected = BigDecimal.ZERO;
            BigDecimal pScheduled = BigDecimal.ZERO;
            BigDecimal pProjected = BigDecimal.ZERO;

            for (LocalDate day = periodStart; !day.isAfter(periodEnd); day = day.plusDays(1)) {
                BigDecimal dConfirmed = confirmed.getOrDefault(day, BigDecimal.ZERO);
                BigDecimal dExpected = expected.getOrDefault(day, BigDecimal.ZERO);
                BigDecimal dScheduled = scheduled.getOrDefault(day, BigDecimal.ZERO);
                BigDecimal dProjected = in.averageDailyExpense();
                balance = balance.add(dConfirmed).add(dExpected).subtract(dScheduled).subtract(dProjected);
                if (lowest == null || balance.compareTo(lowest) < 0) {
                    lowest = balance;
                    lowestDate = day;
                }
                pConfirmed = pConfirmed.add(dConfirmed);
                pExpected = pExpected.add(dExpected);
                pScheduled = pScheduled.add(dScheduled);
                pProjected = pProjected.add(dProjected);
            }

            BigDecimal net = pConfirmed.add(pExpected).subtract(pScheduled).subtract(pProjected);
            periods.add(new ForecastPeriod(periodStart, periodEnd, money(periodOpening), money(pConfirmed),
                    money(pExpected), money(pScheduled), money(pProjected), money(net), money(balance)));
            totalConfirmed = totalConfirmed.add(pConfirmed);
            totalExpected = totalExpected.add(pExpected);
            totalScheduled = totalScheduled.add(pScheduled);
            totalProjected = totalProjected.add(pProjected);
            periodStart = periodEnd.plusDays(1);
        }

        List<ExpectedInvoice> invoiceViews = in.invoices().stream()
                .map(i -> {
                    LocalDate date = overrides.getOrDefault(i.invoiceId(), i.expectedDate());
                    return new ExpectedInvoice(i.invoiceId(), i.invoiceNumber(), i.customerId(), i.customerName(),
                            i.outstandingAmount(), i.dueDate(), date, i.overdue(), !date.isAfter(in.end()));
                })
                .toList();

        return new CashflowForecastResponse(
                in.days(),
                granularity.name(),
                in.today(),
                in.start(),
                in.end(),
                in.cashIn(),
                in.cashOut(),
                money(opening),
                money(totalConfirmed),
                money(totalExpected),
                money(totalScheduled),
                money(totalProjected),
                in.averageDailyExpense(),
                money(balance),
                money(lowest),
                lowestDate,
                money(beyondHorizon),
                periods,
                invoiceViews,
                List.of(
                        "Opening balance is payments received minus expenses recorded up to the as-of date.",
                        "Unpaid invoice balances are expected (not confirmed) cash, expected on the due date; "
                                + "overdue balances are expected on the first forecast day.",
                        "Projected outflow uses the average daily expense of the last "
                                + EXPENSE_HISTORY_DAYS + " days.",
                        "Future-dated recorded expenses are added as scheduled outflows."));
    }

    private static ScenarioSummary summaryOf(CashflowForecastResponse forecast) {
        return new ScenarioSummary(
                forecast.totalExpectedInflow(),
                forecast.projectedClosingBalance(),
                forecast.lowestProjectedBalance(),
                forecast.lowestBalanceDate());
    }

    private static void validateHorizon(int days) {
        if (!ALLOWED_HORIZONS.contains(days)) {
            throw new BadRequestException("days must be 30, 60 or 90");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private record Inputs(
            int days,
            LocalDate today,
            LocalDate start,
            LocalDate end,
            BigDecimal cashIn,
            BigDecimal cashOut,
            BigDecimal averageDailyExpense,
            List<Payment> futurePayments,
            List<Expense> futureExpenses,
            List<ExpectedInvoice> invoices) {
    }
}
