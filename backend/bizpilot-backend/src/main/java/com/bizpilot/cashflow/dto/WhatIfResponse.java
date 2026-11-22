package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.util.List;

public record WhatIfResponse(
        int horizonDays,
        int delayDays,
        ScenarioSummary baseline,
        ScenarioSummary simulated,
        BigDecimal closingBalanceImpact,
        BigDecimal lowestBalanceImpact,
        BigDecimal inflowPushedBeyondHorizon,
        List<AffectedInvoice> affectedInvoices,
        List<ForecastPeriod> baselinePeriods,
        List<ForecastPeriod> simulatedPeriods) {
}
