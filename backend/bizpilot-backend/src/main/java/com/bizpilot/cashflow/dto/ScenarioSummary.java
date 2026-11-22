package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScenarioSummary(
        BigDecimal totalExpectedInflow,
        BigDecimal projectedClosingBalance,
        BigDecimal lowestProjectedBalance,
        LocalDate lowestBalanceDate) {
}
