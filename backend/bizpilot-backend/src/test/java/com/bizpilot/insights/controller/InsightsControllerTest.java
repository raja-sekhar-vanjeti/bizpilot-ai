package com.bizpilot.insights.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.insights.dto.InsightResponse;
import com.bizpilot.insights.dto.InsightSeverity;
import com.bizpilot.insights.service.InsightsService;
import com.bizpilot.support.ControllerTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InsightsController.class)
class InsightsControllerTest extends ControllerTestSupport {

    @MockitoBean
    private InsightsService insightsService;

    @Test
    void returnsInsightDtos() throws Exception {
        when(insightsService.insights(ownerId)).thenReturn(List.of(new InsightResponse("OVERDUE_INVOICES",
                "Overdue invoices", "1 invoice(s) are past due.", InsightSeverity.CRITICAL, new BigDecimal("30000.00"),
                "Follow up.", LocalDate.of(2026, 2, 1))));

        mockMvc.perform(get("/api/insights").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("OVERDUE_INVOICES"))
                .andExpect(jsonPath("$.data[0].title").value("Overdue invoices"))
                .andExpect(jsonPath("$.data[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data[0].amount").value(30000.00))
                .andExpect(jsonPath("$.data[0].suggestedAction").value("Follow up."))
                .andExpect(jsonPath("$.data[0].relevantDate").value("2026-02-01"));
    }

    @Test
    void emptyInsightsReturnEmptyList() throws Exception {
        when(insightsService.insights(ownerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/insights").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void insightsAreComputedForTokenBusiness() throws Exception {
        UUID otherBusiness = UUID.randomUUID();

        mockMvc.perform(get("/api/insights").header("Authorization", bearer(otherBusiness)))
                .andExpect(status().isOk());
        verify(insightsService).insights(otherBusiness);
    }

    @Test
    void missingOrInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/insights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insights").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(insightsService);
    }
}
