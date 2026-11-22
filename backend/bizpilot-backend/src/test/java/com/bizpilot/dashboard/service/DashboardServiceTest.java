package com.bizpilot.dashboard.service;

import static com.bizpilot.support.TestData.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.customer.repository.CustomerRepository;
import com.bizpilot.dashboard.service.DashboardService.DashboardSummary;
import com.bizpilot.dashboard.service.DashboardService.MonthlyPoint;
import com.bizpilot.dashboard.service.DashboardService.TransactionView;
import com.bizpilot.expense.entity.Expense;
import com.bizpilot.expense.repository.ExpenseRepository;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.repository.PaymentRepository;
import com.bizpilot.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CustomerRepository customerRepository;

    private DashboardService service;
    private UUID ownerId;
    private User owner;
    private Customer customer;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new DashboardService(invoiceRepository, paymentRepository, expenseRepository, customerRepository);
        ownerId = UUID.randomUUID();
        owner = TestData.user(ownerId);
        customer = TestData.customer(owner, "Acme");
        today = LocalDate.now();
        // Default for any business: no customers, no expenses.
        when(customerRepository.findByOwnerIdAndArchivedFalse(any(), any())).thenReturn(Page.empty());
        when(expenseRepository.findByOwnerId(any(), any())).thenReturn(Page.empty());
    }

    private void stubBusinessData() {
        Invoice paid = TestData.invoice(owner, customer, "INV-1", InvoiceStatus.PAID, "10000.00", "0.00",
                today.minusDays(10));
        Invoice sent = TestData.invoice(owner, customer, "INV-2", InvoiceStatus.SENT, "20000.00", "20000.00",
                today.plusDays(10));
        Invoice overdue = TestData.invoice(owner, customer, "INV-3", InvoiceStatus.PARTIALLY_PAID, "15000.00",
                "5000.00", today.minusDays(2));
        when(invoiceRepository.findByOwnerIdAndStatusNotIn(eq(ownerId), any())).thenReturn(List.of(paid, sent, overdue));
        when(invoiceRepository.countByOwnerId(ownerId)).thenReturn(4L);
        when(paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)).thenReturn(List.of(
                TestData.payment(owner, overdue, "10000.00", today.minusDays(1), "UPI-2"),
                TestData.payment(owner, paid, "10000.00", today.minusDays(5), null)));
        List<Expense> expenses = List.of(
                TestData.expense(owner, "Rent", "3000.00", today.minusDays(3)),
                TestData.expense(owner, "Hosting", "2000.00", today));
        when(expenseRepository.findByOwnerId(eq(ownerId), any())).thenReturn(new PageImpl<>(expenses));
        when(customerRepository.findByOwnerIdAndArchivedFalse(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(customer, customer, customer)));
    }

    @Test
    void summaryKeepsInvoicedCollectedAndOutstandingSeparate() {
        stubBusinessData();

        DashboardSummary s = service.summary(ownerId);

        assertEquals(money("45000.00"), s.totalInvoiced());
        assertEquals(money("20000.00"), s.totalCollected());
        assertEquals(money("25000.00"), s.outstandingReceivables());
        assertEquals(money("5000.00"), s.overdueReceivables());
        assertEquals(money("5000.00"), s.totalExpenses());
        assertEquals(money("15000.00"), s.netCashMovement());
        assertEquals(3, s.customerCount());
        assertEquals(4, s.invoiceCount());
        assertEquals(1, s.paidInvoiceCount());
        assertEquals(1, s.pendingInvoiceCount());
        assertEquals(1, s.overdueInvoiceCount());
    }

    @Test
    void summaryExcludesDraftAndCancelledInvoices() {
        stubBusinessData();

        service.summary(ownerId);

        verify(invoiceRepository).findByOwnerIdAndStatusNotIn(ownerId,
                List.of(InvoiceStatus.CANCELLED, InvoiceStatus.DRAFT));
    }

    @Test
    void chartSeparatesRevenueFromExpensesAndUsesPaymentsNotInvoices() {
        stubBusinessData();
        when(paymentRepository.findByOwnerIdOrderByPaymentDateDesc(ownerId)).thenReturn(List.of(
                TestData.payment(owner, null, "1000.00", today, "R1"),
                TestData.payment(owner, null, "999.00", today.minusMonths(13), "OLD")));

        List<MonthlyPoint> points = service.monthlyChart(ownerId);

        assertEquals(12, points.size());
        MonthlyPoint current = points.get(11);
        assertEquals(YearMonth.now().toString(), current.month());
        assertEquals(money("1000.00"), current.revenue());
        assertEquals(sumExpensesThisMonth(), current.expenses());
        assertTrue(points.stream().noneMatch(p -> p.revenue().compareTo(money("999.00")) == 0));
        verifyNoInteractions(invoiceRepository);
    }

    // Both seeded expenses fall in the current month unless today is within the first 3 days.
    private BigDecimal sumExpensesThisMonth() {
        BigDecimal total = money("2000.00");
        if (YearMonth.from(today.minusDays(3)).equals(YearMonth.now())) {
            total = total.add(money("3000.00"));
        }
        return total;
    }

    @Test
    void recentTransactionsMergesPaymentsAndExpensesNewestFirst() {
        stubBusinessData();

        List<TransactionView> recent = service.recentTransactions(ownerId, 3);

        assertEquals(3, recent.size());
        assertEquals("EXPENSE", recent.get(0).type());
        assertEquals("Hosting", recent.get(0).reference());
        assertEquals("PAYMENT", recent.get(1).type());
        assertEquals("UPI-2", recent.get(1).reference());
        assertEquals("EXPENSE", recent.get(2).type());
        for (int i = 1; i < recent.size(); i++) {
            assertTrue(!recent.get(i).date().isAfter(recent.get(i - 1).date()));
        }
    }

    @Test
    void recentTransactionsUsesEmptyReferenceForNullAndHonoursLimit() {
        stubBusinessData();

        List<TransactionView> all = service.recentTransactions(ownerId, 10);

        assertEquals(4, all.size());
        assertTrue(all.stream().anyMatch(t -> t.type().equals("PAYMENT") && t.reference().isEmpty()));
        assertTrue(service.recentTransactions(ownerId, 0).isEmpty());
    }

    @Test
    void emptyBusinessReturnsZeros() {
        DashboardSummary s = service.summary(ownerId);

        assertEquals(money("0.00"), s.totalInvoiced());
        assertEquals(money("0.00"), s.totalCollected());
        assertEquals(money("0.00"), s.outstandingReceivables());
        assertEquals(money("0.00"), s.netCashMovement());
        assertEquals(0, s.customerCount());
        assertEquals(0, s.invoiceCount());
        assertTrue(service.recentTransactions(ownerId, 10).isEmpty());
        assertTrue(service.monthlyChart(ownerId).stream()
                .allMatch(p -> p.revenue().signum() == 0 && p.expenses().signum() == 0));
    }

    @Test
    void anotherBusinessSeesNoneOfThisBusinessData() {
        stubBusinessData();
        UUID otherOwner = UUID.randomUUID();

        DashboardSummary s = service.summary(otherOwner);
        List<TransactionView> recent = service.recentTransactions(otherOwner, 10);

        assertEquals(money("0.00"), s.totalInvoiced());
        assertEquals(money("0.00"), s.totalCollected());
        assertEquals(money("0.00"), s.totalExpenses());
        assertTrue(recent.isEmpty());
        verify(paymentRepository, never()).findByOwnerIdOrderByPaymentDateDesc(ownerId);
        verify(expenseRepository, never()).findByOwnerId(eq(ownerId), any());
    }
}
