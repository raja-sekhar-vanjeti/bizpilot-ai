package com.bizpilot.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashflowForecastResponse(
        int horizonDays,
        String granularity,
        LocalDate asOfDate,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal confirmedCashIn,
        BigDecimal confirmedCashOut,
        BigDecimal openingBalance,
        BigDecimal totalConfirmedInflow,
        BigDecimal totalExpectedInflow,
        BigDecimal totalScheduledOutflow,
        BigDecimal totalProjectedOutflow,
        BigDecimal averageDailyExpense,
        BigDecimal projectedClosingBalance,
        BigDecimal lowestProjectedBalance,
        LocalDate lowestBalanceDate,
        BigDecimal expectedInflowBeyondHorizon,
        List<ForecastPeriod> periods,
        List<ExpectedInvoice> expectedInvoices,
        List<String> assumptions) {
}
