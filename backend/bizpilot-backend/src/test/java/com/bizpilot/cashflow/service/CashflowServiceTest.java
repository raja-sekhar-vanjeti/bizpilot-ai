package com.bizpilot.cashflow.service;

import static com.bizpilot.support.TestData.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.cashflow.dto.AffectedInvoice;
import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.dto.ExpectedInvoice;
import com.bizpilot.cashflow.dto.ForecastGranularity;
import com.bizpilot.cashflow.dto.ForecastPeriod;
import com.bizpilot.cashflow.dto.WhatIfRequest;
import com.bizpilot.cashflow.dto.WhatIfResponse;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.customer.repository.CustomerRepository;
import com.bizpilot.expense.repository.ExpenseRepository;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.repository.PaymentRepository;
import com.bizpilot.support.TestData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashflowServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CustomerRepository customerRepository;

    private CashflowService service;
    private UUID ownerId;
    private UUID otherOwnerId;
    private User owner;
    private Customer acme;
    private Invoice dueSoon;
    private Invoice overdue;
    private Invoice farFuture;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new CashflowService(invoiceRepository, paymentRepository, expenseRepository, customerRepository, clock);
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        owner = TestData.user(ownerId);
        acme = TestData.customer(owner, "Acme");
        Customer beta = TestData.customer(owner, "Beta");
        dueSoon = TestData.invoice(owner, acme, "INV-1", InvoiceStatus.SENT, "30000.00", "30000.00",
                LocalDate.of(2026, 3, 25));
        overdue = TestData.invoice(owner, beta, "INV-2", InvoiceStatus.PARTIALLY_PAID, "8000.00", "5000.00",
                LocalDate.of(2026, 3, 1));
        farFuture = TestData.invoice(owner, beta, "INV-3", InvoiceStatus.SENT, "7000.00", "7000.00",
                LocalDate.of(2026, 5, 30));
    }

    /** Opening 30,000; average daily expense 100; one future payment and one future expense. */
    private void stubStandardData() {
        when(paymentRepository.sumAmountByOwnerIdUpTo(ownerId, TODAY)).thenReturn(money("50000.00"));
        when(expenseRepository.sumAmountByOwnerIdUpTo(ownerId, TODAY)).thenReturn(money("20000.00"));
        when(expenseRepository.sumAmountByOwnerIdBetween(ownerId, TODAY.minusDays(89), TODAY))
                .thenReturn(money("9000.00"));
        when(paymentRepository.findByOwnerIdAndPaymentDateBetween(eq(ownerId), any(), any()))
                .thenReturn(List.of(TestData.payment(owner, dueSoon, "1000.00", LocalDate.of(2026, 3, 20), "R1")));
        when(expenseRepository.findByOwnerIdAndExpenseDateBetweenOrderByExpenseDateAsc(eq(ownerId), any(), any()))
                .thenReturn(List.of(TestData.expense(owner, "Laptop", "2000.00", LocalDate.of(2026, 3, 18))));
        when(invoiceRepository.findByOwnerIdAndStatusNotIn(eq(ownerId), any()))
                .thenReturn(List.of(dueSoon, overdue, farFuture));
    }

    @Test
    void rejectsHorizonOtherThan30_60_90WithoutQuerying() {
        assertThrows(BadRequestException.class, () -> service.forecast(ownerId, 45, null));
        assertThrows(BadRequestException.class, () -> service.forecast(ownerId, 0, null));
        verifyNoInteractions(invoiceRepository, paymentRepository, expenseRepository);
    }

    @Test
    void forecastSeparatesConfirmedFromExpectedAndProjectsClosingBalance() {
        stubStandardData();

        CashflowForecastResponse f = service.forecast(ownerId, 30, ForecastGranularity.WEEKLY);

        assertEquals(LocalDate.of(2026, 3, 16), f.startDate());
        assertEquals(LocalDate.of(2026, 4, 14), f.endDate());
        assertEquals(money("30000.00"), f.openingBalance());
        assertEquals(money("50000.00"), f.confirmedCashIn());
        assertEquals(money("20000.00"), f.confirmedCashOut());
        // Only the recorded payment is confirmed; unpaid invoices are expected only.
        assertEquals(money("1000.00"), f.totalConfirmedInflow());
        assertEquals(money("35000.00"), f.totalExpectedInflow());
        assertEquals(money("7000.00"), f.expectedInflowBeyondHorizon());
        assertEquals(money("2000.00"), f.totalScheduledOutflow());
        assertEquals(money("100.00"), f.averageDailyExpense());
        assertEquals(money("3000.00"), f.totalProjectedOutflow());
        assertEquals(money("61000.00"), f.projectedClosingBalance());
        assertEquals(money("32600.00"), f.lowestProjectedBalance());
        assertEquals(LocalDate.of(2026, 3, 19), f.lowestBalanceDate());
    }

    @Test
    void weeklyBreakdownChainsBalancesAndDailyHasOneRowPerDay() {
        stubStandardData();

        CashflowForecastResponse weekly = service.forecast(ownerId, 30, ForecastGranularity.WEEKLY);
        assertEquals("WEEKLY", weekly.granularity());
        assertEquals(5, weekly.periods().size());
        ForecastPeriod first = weekly.periods().get(0);
        assertEquals(LocalDate.of(2026, 3, 22), first.periodEnd());
        assertEquals(money("30000.00"), first.openingBalance());
        assertEquals(money("1000.00"), first.confirmedInflow());
        assertEquals(money("5000.00"), first.expectedInflow());
        assertEquals(money("2000.00"), first.scheduledOutflow());
        assertEquals(money("700.00"), first.projectedOutflow());
        assertEquals(money("3300.00"), first.netChange());
        assertEquals(money("33300.00"), first.closingBalance());
        for (int i = 1; i < weekly.periods().size(); i++) {
            assertEquals(weekly.periods().get(i - 1).closingBalance(), weekly.periods().get(i).openingBalance());
        }
        assertEquals(LocalDate.of(2026, 4, 14), weekly.periods().get(4).periodEnd());

        CashflowForecastResponse daily = service.forecast(ownerId, 90, ForecastGranularity.DAILY);
        assertEquals(90, daily.periods().size());
        assertEquals(daily.projectedClosingBalance(), daily.periods().get(89).closingBalance());
    }

    @Test
    void overdueInvoiceIsExpectedOnFirstForecastDayAndFlagged() {
        stubStandardData();

        CashflowForecastResponse f = service.forecast(ownerId, 60, null);

        ExpectedInvoice view = f.expectedInvoices().stream()
                .filter(i -> i.invoiceNumber().equals("INV-2")).findFirst().orElseThrow();
        assertTrue(view.overdue());
        assertEquals(LocalDate.of(2026, 3, 16), view.expectedDate());
        assertEquals("WEEKLY", f.granularity());
    }

    @Test
    void emptyBusinessProducesZeroForecast() {
        CashflowForecastResponse f = service.forecast(ownerId, 30, null);

        assertEquals(money("0.00"), f.openingBalance());
        assertEquals(money("0.00"), f.projectedClosingBalance());
        assertEquals(money("0.00"), f.lowestProjectedBalance());
        assertEquals(5, f.periods().size());
        assertTrue(f.expectedInvoices().isEmpty());
    }

    @Test
    void forecastOnlyUsesTheRequestingBusinessData() {
        stubStandardData();

        CashflowForecastResponse other = service.forecast(otherOwnerId, 30, null);

        assertEquals(money("0.00"), other.projectedClosingBalance());
        assertTrue(other.expectedInvoices().isEmpty());
        verify(invoiceRepository).findByOwnerIdAndStatusNotIn(eq(otherOwnerId), any());
        verify(invoiceRepository, never()).findByOwnerIdAndStatusNotIn(eq(ownerId), any());
    }

    @Test
    void whatIfDelayWithinHorizonLowersTroughButNotClosingBalance() {
        stubStandardData();
        when(customerRepository.findByIdAndOwnerId(acme.getId(), ownerId)).thenReturn(Optional.of(acme));

        WhatIfResponse r = service.whatIf(ownerId, new WhatIfRequest(15, 30, acme.getId(), null));

        assertEquals(1, r.affectedInvoices().size());
        AffectedInvoice a = r.affectedInvoices().get(0);
        assertEquals("INV-1", a.invoiceNumber());
        assertEquals(LocalDate.of(2026, 3, 25), a.originalExpectedDate());
        assertEquals(LocalDate.of(2026, 4, 9), a.revisedExpectedDate());
        assertFalse(a.movedBeyondHorizon());
        assertEquals(0, r.closingBalanceImpact().signum());
        assertEquals(money("32600.00"), r.baseline().lowestProjectedBalance());
        assertEquals(money("31600.00"), r.simulated().lowestProjectedBalance());
        assertEquals(LocalDate.of(2026, 4, 8), r.simulated().lowestBalanceDate());
        assertEquals(money("-1000.00"), r.lowestBalanceImpact());
    }

    @Test
    void whatIfDelayBeyondHorizonReducesClosingBalance() {
        stubStandardData();

        WhatIfResponse r = service.whatIf(ownerId, new WhatIfRequest(30, null, null, List.of(dueSoon.getId())));

        assertEquals(30, r.horizonDays());
        assertTrue(r.affectedInvoices().get(0).movedBeyondHorizon());
        assertEquals(money("30000.00"), r.inflowPushedBeyondHorizon());
        assertEquals(money("-30000.00"), r.closingBalanceImpact());
        assertEquals(money("5000.00"), r.simulated().totalExpectedInflow());
    }

    @Test
    void whatIfNeverWritesRecords() {
        stubStandardData();

        service.whatIf(ownerId, new WhatIfRequest(10, 90, null, null));

        verify(invoiceRepository, never()).save(any());
        verify(invoiceRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
        assertEquals(money("30000.00"), dueSoon.getOutstandingAmount());
        assertEquals(LocalDate.of(2026, 3, 25), dueSoon.getDueDate());
    }

    @Test
    void whatIfRejectsAnotherBusinessCustomer() {
        stubStandardData();
        UUID foreignCustomer = UUID.randomUUID();
        when(customerRepository.findByIdAndOwnerId(foreignCustomer, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.whatIf(ownerId, new WhatIfRequest(15, 30, foreignCustomer, null)));
    }

    @Test
    void whatIfRejectsInvoiceNotOwnedOrNotOpen() {
        stubStandardData();

        assertThrows(NotFoundException.class,
                () -> service.whatIf(ownerId, new WhatIfRequest(15, 30, null, List.of(UUID.randomUUID()))));
    }

    @Test
    void whatIfValidatesHorizon() {
        assertThrows(BadRequestException.class,
                () -> service.whatIf(ownerId, new WhatIfRequest(15, 14, null, null)));
    }

    @Test
    void whatIfWithNoOpenInvoicesHasNoImpact() {
        WhatIfResponse r = service.whatIf(ownerId, new WhatIfRequest(15, 30, null, null));

        assertTrue(r.affectedInvoices().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), r.closingBalanceImpact());
    }
}
