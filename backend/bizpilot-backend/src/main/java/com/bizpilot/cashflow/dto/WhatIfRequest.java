package com.bizpilot.cashflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Read-only payment-delay simulation. When {@code invoiceIds} is given only those invoices are
 * delayed; otherwise every open invoice of {@code customerId}; otherwise every open invoice.
 */
public record WhatIfRequest(
        @NotNull @Min(1) @Max(365) Integer delayDays,
        Integer days,
        UUID customerId,
        @Size(max = 200) List<UUID> invoiceIds) {
}
