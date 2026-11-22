package com.bizpilot.payment.dto;

import com.bizpilot.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID invoiceId, BigDecimal amount, LocalDate paymentDate, String method, String reference, String notes, LocalDateTime createdAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getInvoice().getId(), payment.getAmount(), payment.getPaymentDate(), payment.getMethod().name(), payment.getReference(), payment.getNotes(), payment.getCreatedAt());
    }
}
