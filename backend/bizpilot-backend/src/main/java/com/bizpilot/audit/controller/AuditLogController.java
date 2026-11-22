package com.bizpilot.audit.controller;

import com.bizpilot.audit.dto.AuditLogResponse;
import com.bizpilot.audit.service.AuditService;
import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID ownerId = currentUserId(authentication);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AuditLogResponse> result = auditService.list(
                ownerId, entityType, PageRequest.of(safePage, safeSize));
        return ResponseEntity.ok(ApiResponse.ok("Audit logs loaded", result));
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
