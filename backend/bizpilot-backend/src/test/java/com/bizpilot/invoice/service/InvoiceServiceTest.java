package com.bizpilot.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.repository.CustomerRepository;
import com.bizpilot.invoice.dto.InvoiceItemRequest;
import com.bizpilot.invoice.dto.InvoiceRequest;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceItem;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    private InvoiceService invoiceService;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID invoiceId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, customerRepository, userRepository);
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Owner Person");
        user.setBusinessName("Owner Biz");
        user.setEmail("owner@example.com");
        return user;
    }

    private static InvoiceItem item(String lineTotal) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Service");
        item.setQuantity(BigDecimal.ONE.setScale(2));
        item.setUnitPrice(new BigDecimal(lineTotal));
        item.setLineTotal(new BigDecimal(lineTotal));
        return item;
    }

    /** Invoice with taxPercent 18.00 and two items totalling 15000.00 subtotal. */
    private Invoice invoiceWithTwoItems() {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setTaxPercent(new BigDecimal("18.00"));
        invoice.getItems().add(item("10000.00"));
        invoice.getItems().add(item("5000.00"));
        return invoice;
    }

    private static InvoiceRequest request(UUID customerId) {
        return new InvoiceRequest(
                customerId,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                new BigDecimal("18.00"),
                BigDecimal.ZERO,
                "INR",
                "Net 30",
                List.of(new InvoiceItemRequest("Consulting", new BigDecimal("1.00"), new BigDecimal("100.00"))));
    }

    @Test
    void calculateTotalsWithNoDiscountComputesSubtotalTaxAndTotal() {
        Invoice invoice = invoiceWithTwoItems();

        invoiceService.calculateTotals(invoice, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("15000.00").compareTo(invoice.getSubtotal()));
        assertEquals(0, new BigDecimal("2700.00").compareTo(invoice.getTaxAmount()));
        assertEquals(0, new BigDecimal("17700.00").compareTo(invoice.getTotalAmount()));
        assertEquals(0, new BigDecimal("17700.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void calculateTotalsAppliesDiscountBeforeTax() {
        Invoice invoice = invoiceWithTwoItems();

        invoiceService.calculateTotals(invoice, new BigDecimal("1000.00"));

        assertEquals(0, new BigDecimal("2520.00").compareTo(invoice.getTaxAmount()));
        assertEquals(0, new BigDecimal("16520.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    void calculateTotalsRejectsDiscountGreaterThanSubtotal() {
        Invoice invoice = invoiceWithTwoItems();

        assertThrows(ConflictException.class,
                () -> invoiceService.calculateTotals(invoice, new BigDecimal("15000.01")));
    }

    @Test
    void calculateTotalsSubtractsPaidAmountFromOutstanding() {
        Invoice invoice = invoiceWithTwoItems();
        invoice.setPaidAmount(new BigDecimal("10000.00"));

        invoiceService.calculateTotals(invoice, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("7700.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void createWithUnknownCustomerThrowsNotFoundAndNeverSaves() {
        lenient().when(userRepository.findById(ownerId)).thenReturn(Optional.of(user(ownerId)));
        when(customerRepository.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> invoiceService.create(ownerId, request(customerId)));

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void getInvoiceOwnedByAnotherBusinessThrowsNotFoundAndUsesCallerOwnerId() {
        // Invoice belongs to otherOwnerId; the caller is ownerId, so the scoped lookup finds nothing.
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> invoiceService.get(ownerId, invoiceId));

        verify(invoiceRepository).findByIdAndOwnerId(invoiceId, ownerId);
        verify(invoiceRepository, never()).findByIdAndOwnerId(invoiceId, otherOwnerId);
    }

    @Test
    void markSentOnNonDraftInvoiceThrowsConflictAndNeverSaves() {
        Invoice sent = invoiceWithTwoItems();
        sent.setStatus(InvoiceStatus.SENT);
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(sent));

        assertThrows(ConflictException.class, () -> invoiceService.markSent(ownerId, invoiceId));

        verify(invoiceRepository, never()).save(any());
        assertTrue(sent.getStatus() == InvoiceStatus.SENT);
    }

    @Test
    void cancelInvoiceWithPaymentsThrowsConflict() {
        Invoice partlyPaid = invoiceWithTwoItems();
        partlyPaid.setStatus(InvoiceStatus.PARTIALLY_PAID);
        partlyPaid.setPaidAmount(new BigDecimal("500.00"));
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(partlyPaid));

        assertThrows(ConflictException.class, () -> invoiceService.cancel(ownerId, invoiceId));

        verify(invoiceRepository, never()).save(any());
    }
}
