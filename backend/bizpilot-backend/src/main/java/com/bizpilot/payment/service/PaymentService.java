package com.bizpilot.payment.service;

import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.payment.entity.Payment;
import com.bizpilot.payment.entity.PaymentMethod;
import com.bizpilot.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public Payment record(UUID ownerId, UUID invoiceId, BigDecimal amount, LocalDate paymentDate,
                          PaymentMethod method, String reference, String notes) {
        Invoice invoice = invoiceRepository.findByIdAndOwnerId(invoiceId, ownerId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));

        InvoiceStatus status = invoice.getStatus();
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED || status == InvoiceStatus.PAID) {
            throw new ConflictException("Payments cannot be recorded for this invoice");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ConflictException("Payment amount must be greater than zero");
        }

        BigDecimal payment = amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal outstanding = invoice.getOutstandingAmount();
        if (payment.compareTo(outstanding) > 0) {
            throw new ConflictException("Payment exceeds outstanding amount");
        }

        Payment entity = new Payment();
        entity.setOwner(invoice.getOwner());
        entity.setInvoice(invoice);
        entity.setAmount(payment);
        entity.setPaymentDate(paymentDate == null ? LocalDate.now() : paymentDate);
        entity.setMethod(method);
        entity.setReference(reference);
        entity.setNotes(notes);
        Payment saved = paymentRepository.save(entity);

        BigDecimal newPaid = invoice.getPaidAmount().add(payment).setScale(MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal newOutstanding = outstanding.subtract(payment).setScale(MONEY_SCALE, MONEY_ROUNDING);
        invoice.setPaidAmount(newPaid);
        invoice.setOutstandingAmount(newOutstanding);
        if (newOutstanding.signum() == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Payment> listForInvoice(UUID ownerId, UUID invoiceId) {
        return paymentRepository.findByInvoiceIdAndOwnerId(invoiceId, ownerId);
    }

    @Transactional(readOnly = true)
    public Payment getById(UUID ownerId, UUID paymentId) {
        return paymentRepository.findByIdAndOwnerId(paymentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public Page<Payment> list(UUID ownerId, Pageable pageable) {
        return paymentRepository.findByOwnerId(ownerId, pageable);
    }
}
