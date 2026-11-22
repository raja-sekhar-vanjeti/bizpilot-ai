package com.bizpilot.insights.service;

import static com.bizpilot.support.TestData.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.service.CashflowService;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.expense.repository.ExpenseRepository;
import com.bizpilot.insights.dto.InsightResponse;
import com.bizpilot.insights.dto.InsightSeverity;
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
class InsightsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);
    private static final LocalDate WINDOW_START = TODAY.minusDays(29);
    private static final LocalDate PREVIOUS_END = WINDOW_START.minusDays(1);

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CashflowService cashflowService;

    private InsightsService service;
    private UUID ownerId;
    private User owner;
    private Customer acme;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new InsightsService(invoiceRepository, paymentRepository, expenseRepository, cashflowService, clock);
        ownerId = UUID.randomUUID();
        owner = TestData.user(ownerId);
        acme = TestData.customer(owner, "Acme");
        stubForecast(any(), "0.00", "0.00");
    }

    private void stubForecast(UUID matcherOwner, String lowest, String averageDaily) {
        CashflowForecastResponse forecast = new CashflowForecastResponse(30, "WEEKLY", TODAY, TODAY.plusDays(1),
                TODAY.plusDays(30), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, money(averageDaily), BigDecimal.ZERO,
                money(lowest), TODAY.plusDays(10), BigDecimal.ZERO, List.of(), List.of(), List.of());
        when(cashflowService.forecast(matcherOwner, anyInt(), any())).thenReturn(forecast);
    }

    private void invoices(Invoice... invoices) {
        when(invoiceRepository.findByOwnerIdAndStatusNotIn(eq(ownerId), any())).thenReturn(List.of(invoices));
    }

    private static Optional<InsightResponse> find(List<InsightResponse> insights, String code) {
        return insights.stream().filter(i -> i.code().equals(code)).findFirst();
    }

    @Test
    void emptyBusinessHasNoInsights() {
        assertTrue(service.insights(ownerId).isEmpty());
    }

    @Test
    void longOverdueInvoiceIsCritical() {
        invoices(TestData.invoice(owner, acme, "INV-1", InvoiceStatus.OVERDUE, "10000.00", "10000.00",
                TODAY.minusDays(45)));

        InsightResponse insight = find(service.insights(ownerId), "OVERDUE_INVOICES").orElseThrow();

        assertEquals(InsightSeverity.CRITICAL, insight.severity());
        assertEquals(money("10000.00"), insight.amount());
        assertEquals(TODAY.minusDays(45), insight.relevantDate());
        assertNotNull(insight.suggestedAction());
        assertNotNull(insight.title());
        assertNotNull(insight.description());
    }

    @Test
    void recentlyOverdueInvoiceIsWarning() {
        invoices(TestData.invoice(owner, acme, "INV-1", InvoiceStatus.SENT, "10000.00", "4000.00",
                TODAY.minusDays(3)));

        assertEquals(InsightSeverity.WARNING,
                find(service.insights(ownerId), "OVERDUE_INVOICES").orElseThrow().severity());
    }

    @Test
    void highOutstandingReceivablesFlaggedAtHalfOfInvoiced() {
        invoices(
                TestData.invoice(owner, acme, "INV-1", InvoiceStatus.PAID, "40000.00", "0.00", TODAY.minusDays(20)),
                TestData.invoice(owner, acme, "INV-2", InvoiceStatus.SENT, "60000.00", "60000.00", TODAY.plusDays(20)));

        InsightResponse insight = find(service.insights(ownerId), "HIGH_OUTSTANDING_RECEIVABLES").orElseThrow();

        assertEquals(money("60000.00"), insight.amount());
        assertTrue(insight.description().contains("60%"));
    }

    @Test
    void lowOutstandingIsNotFlagged() {
        invoices(
                TestData.invoice(owner, acme, "INV-1", InvoiceStatus.PAID, "90000.00", "0.00", TODAY.minusDays(20)),
                TestData.invoice(owner, acme, "INV-2", InvoiceStatus.SENT, "10000.00", "10000.00", TODAY.plusDays(20)));

        assertFalse(find(service.insights(ownerId), "HIGH_OUTSTANDING_RECEIVABLES").isPresent());
    }

    @Test
    void customerWithLatePaymentAndOverdueInvoiceIsRepeatedLatePayer() {
        Invoice paidLate = TestData.invoice(owner, acme, "INV-1", InvoiceStatus.PAID, "5000.00", "0.00",
                TODAY.minusDays(40));
        Invoice overdue = TestData.invoice(owner, acme, "INV-2", InvoiceStatus.OVERDUE, "3000.00", "3000.00",
                TODAY.minusDays(5));
        invoices(paidLate, overdue);
        when(paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)).thenReturn(List.of(
                TestData.payment(owner, paidLate, "5000.00", TODAY.minusDays(30), "R1")));

        InsightResponse insight = find(service.insights(ownerId), "REPEATED_LATE_PAYER").orElseThrow();

        assertTrue(insight.title().contains("Acme"));
        assertEquals(money("3000.00"), insight.amount());
    }

    @Test
    void onTimePayerIsNotFlagged() {
        Invoice paidOnTime = TestData.invoice(owner, acme, "INV-1", InvoiceStatus.PAID, "5000.00", "0.00",
                TODAY.minusDays(40));
        invoices(paidOnTime, TestData.invoice(owner, acme, "INV-2", InvoiceStatus.OVERDUE, "3000.00", "3000.00",
                TODAY.minusDays(5)));
        when(paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)).thenReturn(List.of(
                TestData.payment(owner, paidOnTime, "5000.00", TODAY.minusDays(41), "R1")));

        assertFalse(find(service.insights(ownerId), "REPEATED_LATE_PAYER").isPresent());
    }

    @Test
    void expenseSpikeAndSpendingAboveCollectionsAreFlagged() {
        when(expenseRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START, TODAY)).thenReturn(money("15000.00"));
        when(expenseRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START.minusDays(90), PREVIOUS_END))
                .thenReturn(money("30000.00"));
        when(paymentRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START, TODAY)).thenReturn(money("12000.00"));

        List<InsightResponse> insights = service.insights(ownerId);

        assertEquals(money("15000.00"), find(insights, "HIGH_MONTHLY_EXPENSES").orElseThrow().amount());
        assertEquals(money("3000.00"), find(insights, "EXPENSES_EXCEED_COLLECTIONS").orElseThrow().amount());
    }

    @Test
    void collectionsTrendComparesWithPreviousWindow() {
        when(paymentRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START, TODAY)).thenReturn(money("6000.00"));
        when(paymentRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START.minusDays(30), PREVIOUS_END))
                .thenReturn(money("10000.00"));

        InsightResponse insight = find(service.insights(ownerId), "COLLECTIONS_DECLINING").orElseThrow();

        assertEquals(InsightSeverity.WARNING, insight.severity());
        assertTrue(insight.description().contains("40%"));
    }

    @Test
    void collectionsGrowthIsInformational() {
        when(paymentRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START, TODAY)).thenReturn(money("15000.00"));
        when(paymentRepository.sumAmountByOwnerIdBetween(ownerId, WINDOW_START.minusDays(30), PREVIOUS_END))
                .thenReturn(money("10000.00"));

        assertEquals(InsightSeverity.INFO,
                find(service.insights(ownerId), "COLLECTIONS_GROWING").orElseThrow().severity());
    }

    @Test
    void negativeProjectedBalanceIsCriticalAndListedFirst() {
        stubForecast(eq(ownerId), "-2500.00", "100.00");
        invoices(TestData.invoice(owner, acme, "INV-1", InvoiceStatus.SENT, "1000.00", "1000.00", TODAY.plusDays(3)));

        List<InsightResponse> insights = service.insights(ownerId);

        InsightResponse first = insights.get(0);
        assertEquals("LOW_PROJECTED_CASH", first.code());
        assertEquals(InsightSeverity.CRITICAL, first.severity());
        assertEquals(money("-2500.00"), first.amount());
        assertEquals(TODAY.plusDays(10), first.relevantDate());
        assertEquals(InsightSeverity.INFO, insights.get(insights.size() - 1).severity());
    }

    @Test
    void balanceBelowOneMonthOfExpensesIsWarning() {
        stubForecast(eq(ownerId), "2000.00", "100.00");

        assertEquals(InsightSeverity.WARNING,
                find(service.insights(ownerId), "LOW_PROJECTED_CASH").orElseThrow().severity());
    }

    @Test
    void upcomingDueInvoicesWithinSevenDays() {
        invoices(
                TestData.invoice(owner, acme, "INV-1", InvoiceStatus.SENT, "1000.00", "1000.00", TODAY.plusDays(2)),
                TestData.invoice(owner, acme, "INV-2", InvoiceStatus.SENT, "9000.00", "9000.00", TODAY.plusDays(20)));

        InsightResponse insight = find(service.insights(ownerId), "UPCOMING_DUE_INVOICES").orElseThrow();

        assertEquals(money("1000.00"), insight.amount());
        assertEquals(TODAY.plusDays(2), insight.relevantDate());
    }

    @Test
    void insightsUseOnlyTheRequestingBusinessData() {
        invoices(TestData.invoice(owner, acme, "INV-1", InvoiceStatus.OVERDUE, "10000.00", "10000.00",
                TODAY.minusDays(45)));
        UUID otherOwner = UUID.randomUUID();

        assertTrue(service.insights(otherOwner).isEmpty());
        verify(invoiceRepository).findByOwnerIdAndStatusNotIn(eq(otherOwner), any());
        verify(invoiceRepository, never()).findByOwnerIdAndStatusNotIn(eq(ownerId), any());
        verify(cashflowService).forecast(eq(otherOwner), eq(30), any());
    }
}
