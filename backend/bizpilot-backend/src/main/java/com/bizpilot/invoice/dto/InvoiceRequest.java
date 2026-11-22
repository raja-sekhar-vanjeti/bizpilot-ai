package com.bizpilot.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceRequest(
        @NotNull UUID customerId,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") BigDecimal taxPercent,
        @DecimalMin(value = "0.00") BigDecimal discountAmount,
        @Size(max = 3) String currency,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<InvoiceItemRequest> items) {
}
