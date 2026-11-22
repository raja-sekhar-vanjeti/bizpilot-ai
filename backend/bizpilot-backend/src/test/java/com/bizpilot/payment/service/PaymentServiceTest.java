package com.bizpilot.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.audit.service.AuditService;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.entity.PaymentMethod;
import com.bizpilot.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AuditService auditService;

    private PaymentService paymentService;

    private UUID ownerId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, invoiceRepository, auditService);
        ownerId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
    }

    private Invoice invoice(InvoiceStatus status, String total, String paid, String outstanding) {
        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setStatus(status);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(new BigDecimal(paid));
        invoice.setOutstandingAmount(new BigDecimal(outstanding));
        return invoice;
    }

    @Test
    void partialPaymentReducesOutstandingAndMarksPartiallyPaid() {
        Invoice invoice = invoice(InvoiceStatus.SENT, "100.00", "0.00", "100.00");
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment saved = paymentService.record(ownerId, invoiceId, new BigDecimal("40"), LocalDate.of(2024, 1, 5),
                PaymentMethod.UPI, "UTR1", null);

        assertEquals(new BigDecimal("40.00"), saved.getAmount());
        assertEquals(new BigDecimal("40.00"), invoice.getPaidAmount());
        assertEquals(new BigDecimal("60.00"), invoice.getOutstandingAmount());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void fullPaymentMarksInvoicePaid() {
        Invoice invoice = invoice(InvoiceStatus.PARTIALLY_PAID, "100.00", "40.00", "60.00");
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.record(ownerId, invoiceId, new BigDecimal("60"), null, PaymentMethod.CASH, null, null);

        assertEquals(new BigDecimal("100.00"), invoice.getPaidAmount());
        assertEquals(new BigDecimal("0.00"), invoice.getOutstandingAmount());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void overpaymentIsRejectedWithoutSaving() {
        Invoice invoice = invoice(InvoiceStatus.SENT, "100.00", "0.00", "100.00");
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(invoice));

        assertThrows(ConflictException.class, () -> paymentService.record(
                ownerId, invoiceId, new BigDecimal("100.01"), null, PaymentMethod.CARD, null, null));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void draftInvoiceRejectsPayment() {
        Invoice invoice = invoice(InvoiceStatus.DRAFT, "100.00", "0.00", "100.00");
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(invoice));

        assertThrows(ConflictException.class, () -> paymentService.record(
                ownerId, invoiceId, new BigDecimal("10"), null, PaymentMethod.CASH, null, null));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void unknownInvoiceIsNotFound() {
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> paymentService.record(
                ownerId, invoiceId, new BigDecimal("10"), null, PaymentMethod.CASH, null, null));
    }

    @Test
    void partialPaymentOnPastDueInvoiceSetsOverdue() {
        Invoice invoice = invoice(InvoiceStatus.SENT, "20000.00", "0.00", "20000.00");
        invoice.setDueDate(LocalDate.now().minusDays(5));
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.record(ownerId, invoiceId, new BigDecimal("10000.00"), LocalDate.now(),
                PaymentMethod.BANK_TRANSFER, "REF1", null);

        assertEquals(InvoiceStatus.OVERDUE, invoice.getStatus());
        assertEquals(0, invoice.getOutstandingAmount().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, invoice.getPaidAmount().compareTo(new BigDecimal("10000.00")));
    }

    @Test
    void paymentOfAnotherBusinessIsNotFound() {
        UUID paymentId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        when(paymentRepository.findByIdAndOwnerId(paymentId, otherOwnerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> paymentService.getById(otherOwnerId, paymentId));
        verify(paymentRepository).findByIdAndOwnerId(paymentId, otherOwnerId);
    }

    @Test
    void recordingPaymentOnAnotherBusinessInvoiceIsNotFoundAndNeverSaves() {
        UUID otherOwnerId = UUID.randomUUID();
        when(invoiceRepository.findByIdAndOwnerId(invoiceId, otherOwnerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> paymentService.record(otherOwnerId, invoiceId,
                new BigDecimal("100.00"), LocalDate.now(), PaymentMethod.UPI, null, null));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void listingsAreScopedToTheCallerBusiness() {
        UUID otherOwnerId = UUID.randomUUID();

        assertEquals(0, paymentService.listForInvoice(otherOwnerId, invoiceId).size());
        verify(paymentRepository).findByInvoiceIdAndOwnerId(invoiceId, otherOwnerId);
    }
}
