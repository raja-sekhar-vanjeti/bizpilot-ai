package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the forecast breakdown. Confirmed inflows are payments already recorded; expected
 * inflows are unpaid invoice balances and are never treated as received cash.
 */
public record ForecastPeriod(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingBalance,
        BigDecimal confirmedInflow,
        BigDecimal expectedInflow,
        BigDecimal scheduledOutflow,
        BigDecimal projectedOutflow,
        BigDecimal netChange,
        BigDecimal closingBalance) {
}
