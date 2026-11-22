package com.bizpilot.cashflow.controller;

import com.bizpilot.cashflow.dto.CashflowForecastResponse;
import com.bizpilot.cashflow.dto.ForecastGranularity;
import com.bizpilot.cashflow.dto.WhatIfRequest;
import com.bizpilot.cashflow.dto.WhatIfResponse;
import com.bizpilot.cashflow.service.CashflowService;
import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cashflow")
public class CashflowController {

    private final CashflowService cashflowService;

    public CashflowController(CashflowService cashflowService) {
        this.cashflowService = cashflowService;
    }

    @GetMapping("/forecast")
    public ResponseEntity<ApiResponse<CashflowForecastResponse>> forecast(
            Authentication authentication,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) ForecastGranularity granularity) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Cash-flow forecast",
                cashflowService.forecast(ownerId, days, granularity)));
    }

    // Read-only simulation: nothing is persisted, so POST returns 200 rather than 201.
    @PostMapping("/what-if")
    public ResponseEntity<ApiResponse<WhatIfResponse>> whatIf(
            Authentication authentication,
            @Valid @RequestBody WhatIfRequest request) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("What-if simulation", cashflowService.whatIf(ownerId, request)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
