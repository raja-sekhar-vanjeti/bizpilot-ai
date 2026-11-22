package com.bizpilot.insights.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.insights.dto.InsightResponse;
import com.bizpilot.insights.service.InsightsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InsightResponse>>> insights(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Financial insights", insightsService.insights(ownerId)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
