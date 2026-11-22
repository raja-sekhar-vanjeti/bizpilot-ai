package com.bizpilot.dashboard.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.dashboard.service.DashboardService;
import com.bizpilot.dashboard.service.DashboardService.DashboardSummary;
import com.bizpilot.dashboard.service.DashboardService.MonthlyPoint;
import com.bizpilot.dashboard.service.DashboardService.TransactionView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Dashboard summary", dashboardService.summary(ownerId)));
    }

    @GetMapping("/recent-transactions")
    public ResponseEntity<ApiResponse<List<TransactionView>>> recentTransactions(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Recent transactions", dashboardService.recentTransactions(ownerId, limit)));
    }

    @GetMapping("/revenue-chart")
    public ResponseEntity<ApiResponse<List<MonthlyPoint>>> revenueChart(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Revenue chart", dashboardService.monthlyChart(ownerId)));
    }

    @GetMapping("/expense-chart")
    public ResponseEntity<ApiResponse<List<MonthlyPoint>>> expenseChart(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Expense chart", dashboardService.monthlyChart(ownerId)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
