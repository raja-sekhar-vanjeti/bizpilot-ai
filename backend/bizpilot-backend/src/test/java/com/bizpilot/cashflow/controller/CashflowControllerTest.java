package com.bizpilot.cashflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.cashflow.dto.AffectedInvoice;
import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.dto.ForecastGranularity;
import com.bizpilot.cashflow.dto.ForecastPeriod;
import com.bizpilot.cashflow.dto.ScenarioSummary;
import com.bizpilot.cashflow.dto.WhatIfRequest;
import com.bizpilot.cashflow.dto.WhatIfResponse;
import com.bizpilot.cashflow.service.CashflowService;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.support.ControllerTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(CashflowController.class)
class CashflowControllerTest extends ControllerTestSupport {

    @MockitoBean
    private CashflowService cashflowService;

    private static final LocalDate D = LocalDate.of(2026, 3, 16);

    private static CashflowForecastResponse forecast() {
        BigDecimal z = new BigDecimal("0.00");
        ForecastPeriod period = new ForecastPeriod(D, D.plusDays(6), new BigDecimal("1000.00"), z,
                new BigDecimal("500.00"), z, new BigDecimal("70.00"), new BigDecimal("430.00"), new BigDecimal("1430.00"));
        return new CashflowForecastResponse(30, "WEEKLY", D.minusDays(1), D, D.plusDays(29), z, z,
                new BigDecimal("1000.00"), z, new BigDecimal("500.00"), z, new BigDecimal("300.00"),
                new BigDecimal("10.00"), new BigDecimal("1200.00"), new BigDecimal("930.00"), D.plusDays(5),
                z, List.of(period), List.of(), List.of("note"));
    }

    @Test
    void forecastReturnsDtoForAuthenticatedBusiness() throws Exception {
        when(cashflowService.forecast(ownerId, 30, ForecastGranularity.WEEKLY)).thenReturn(forecast());

        mockMvc.perform(get("/api/cashflow/forecast").param("days", "30").param("granularity", "WEEKLY")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.horizonDays").value(30))
                .andExpect(jsonPath("$.data.openingBalance").value(1000.00))
                .andExpect(jsonPath("$.data.projectedClosingBalance").value(1200.00))
                .andExpect(jsonPath("$.data.periods[0].expectedInflow").value(500.00))
                .andExpect(jsonPath("$.data.owner").doesNotExist());
    }

    @Test
    void forecastDefaultsToThirtyDays() throws Exception {
        when(cashflowService.forecast(ownerId, 30, null)).thenReturn(forecast());

        mockMvc.perform(get("/api/cashflow/forecast").header("Authorization", bearer()))
                .andExpect(status().isOk());
        verify(cashflowService).forecast(ownerId, 30, null);
    }

    @Test
    void forecastWithUnsupportedHorizonIsBadRequest() throws Exception {
        when(cashflowService.forecast(eq(ownerId), eq(45), any()))
                .thenThrow(new BadRequestException("days must be 30, 60 or 90"));

        mockMvc.perform(get("/api/cashflow/forecast").param("days", "45").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("days must be 30, 60 or 90"));
    }

    @Test
    void forecastWithNonNumericDaysOrBadGranularityIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/cashflow/forecast").param("days", "abc").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/cashflow/forecast").param("granularity", "HOURLY").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cashflowService);
    }

    @Test
    void forecastWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cashflow/forecast"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(cashflowService);
    }

    @Test
    void forecastWithTamperedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cashflow/forecast").header("Authorization", bearer() + "x"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cashflowService);
    }

    @Test
    void whatIfReturnsImpact() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        BigDecimal z = new BigDecimal("0.00");
        WhatIfResponse response = new WhatIfResponse(30, 15,
                new ScenarioSummary(new BigDecimal("30000.00"), new BigDecimal("61000.00"), new BigDecimal("32600.00"), D),
                new ScenarioSummary(new BigDecimal("30000.00"), new BigDecimal("61000.00"), new BigDecimal("31600.00"), D),
                z, new BigDecimal("-1000.00"), z,
                List.of(new AffectedInvoice(invoiceId, "INV-1", "Acme", new BigDecimal("30000.00"),
                        D.plusDays(9), D.plusDays(24), false)),
                List.of(), List.of());
        when(cashflowService.whatIf(eq(ownerId), any(WhatIfRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cashflow/what-if").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayDays\":15,\"days\":30,\"invoiceIds\":[\"" + invoiceId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delayDays").value(15))
                .andExpect(jsonPath("$.data.lowestBalanceImpact").value(-1000.00))
                .andExpect(jsonPath("$.data.affectedInvoices[0].invoiceNumber").value("INV-1"))
                .andExpect(jsonPath("$.data.affectedInvoices[0].revisedExpectedDate").value("2026-04-09"));
    }

    @Test
    void whatIfIgnoresOwnerIdInBodyAndUsesTokenIdentity() throws Exception {
        when(cashflowService.whatIf(any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/cashflow/what-if").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayDays\":5,\"ownerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        verify(cashflowService).whatIf(eq(ownerId), any());
    }

    @Test
    void whatIfValidatesDelay() throws Exception {
        for (String body : List.of("{}", "{\"delayDays\":0}", "{\"delayDays\":400}")) {
            mockMvc.perform(post("/api/cashflow/what-if").header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        mockMvc.perform(post("/api/cashflow/what-if").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cashflowService);
    }

    @Test
    void whatIfForUnknownCustomerIsNotFound() throws Exception {
        when(cashflowService.whatIf(eq(ownerId), any())).thenThrow(new NotFoundException("Customer not found"));

        mockMvc.perform(post("/api/cashflow/what-if").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayDays\":5,\"customerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void whatIfWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/cashflow/what-if").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayDays\":5}"))
                .andExpect(status().isUnauthorized());
        verify(cashflowService, org.mockito.Mockito.never()).forecast(any(), anyInt(), any());
    }
}
