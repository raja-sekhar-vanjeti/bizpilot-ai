package com.bizpilot.audit.dto;

import com.bizpilot.audit.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        String entityId,
        String summary,
        LocalDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getSummary(),
                log.getCreatedAt());
    }
}
