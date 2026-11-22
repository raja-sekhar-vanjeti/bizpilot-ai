package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpectedInvoice(
        UUID invoiceId,
        String invoiceNumber,
        UUID customerId,
        String customerName,
        BigDecimal outstandingAmount,
        LocalDate dueDate,
        LocalDate expectedDate,
        boolean overdue,
        boolean withinHorizon) {
}
