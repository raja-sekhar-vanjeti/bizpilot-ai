package com.bizpilot.invoice.dto;

import com.bizpilot.invoice.entity.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        String customerName,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        String currency,
        String notes,
        String status,
        LocalDateTime sentAt,
        List<ItemResponse> items) {

    public record ItemResponse(
            UUID id,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public static InvoiceResponse from(Invoice invoice) {
        List<ItemResponse> items = invoice.getItems().stream()
                .map(item -> new ItemResponse(
                        item.getId(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getName(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxPercent(),
                invoice.getTaxAmount(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getOutstandingAmount(),
                invoice.getCurrency(),
                invoice.getNotes(),
                invoice.getStatus().name(),
                invoice.getSentAt(),
                items);
    }
}
