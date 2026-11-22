package com.bizpilot.dashboard.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.dashboard.service.DashboardService;
import com.bizpilot.dashboard.service.DashboardService.DashboardSummary;
import com.bizpilot.dashboard.service.DashboardService.MonthlyPoint;
import com.bizpilot.dashboard.service.DashboardService.TransactionView;
import com.bizpilot.support.ControllerTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest extends ControllerTestSupport {

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void summaryReturnsTotals() throws Exception {
        when(dashboardService.summary(ownerId)).thenReturn(new DashboardSummary(new BigDecimal("45000.00"),
                new BigDecimal("20000.00"), new BigDecimal("25000.00"), new BigDecimal("5000.00"),
                new BigDecimal("5000.00"), new BigDecimal("15000.00"), 3, 4, 1, 1, 1));

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInvoiced").value(45000.00))
                .andExpect(jsonPath("$.data.totalCollected").value(20000.00))
                .andExpect(jsonPath("$.data.netCashMovement").value(15000.00))
                .andExpect(jsonPath("$.data.overdueInvoiceCount").value(1));
    }

    @Test
    void recentTransactionsPassesLimit() throws Exception {
        when(dashboardService.recentTransactions(ownerId, 5)).thenReturn(List.of(
                new TransactionView("PAYMENT", "UPI-1", new BigDecimal("100.00"), LocalDate.of(2026, 3, 1))));

        mockMvc.perform(get("/api/dashboard/recent-transactions").param("limit", "5").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.data[0].date").value("2026-03-01"));
        verify(dashboardService).recentTransactions(ownerId, 5);
    }

    @Test
    void invalidLimitIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/recent-transactions").param("limit", "ten").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void chartsReturnMonthlyPoints() throws Exception {
        when(dashboardService.monthlyChart(ownerId)).thenReturn(List.of(
                new MonthlyPoint("2026-03", new BigDecimal("1000.00"), new BigDecimal("400.00"))));

        mockMvc.perform(get("/api/dashboard/revenue-chart").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revenue").value(1000.00));
        mockMvc.perform(get("/api/dashboard/expense-chart").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].expenses").value(400.00));
    }

    @Test
    void dashboardIsLoadedForTokenBusinessOnly() throws Exception {
        UUID otherBusiness = UUID.randomUUID();

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", bearer(otherBusiness)))
                .andExpect(status().isOk());
        verify(dashboardService).summary(otherBusiness);
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/revenue-chart")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }
}
