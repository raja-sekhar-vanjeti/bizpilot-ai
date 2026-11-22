package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AffectedInvoice(
        UUID invoiceId,
        String invoiceNumber,
        String customerName,
        BigDecimal outstandingAmount,
        LocalDate originalExpectedDate,
        LocalDate revisedExpectedDate,
        boolean movedBeyondHorizon) {
}
