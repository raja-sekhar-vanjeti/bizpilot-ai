package com.bizpilot.insights.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InsightResponse(
        String code,
        String title,
        String description,
        InsightSeverity severity,
        BigDecimal amount,
        String suggestedAction,
        LocalDate relevantDate) {
}
