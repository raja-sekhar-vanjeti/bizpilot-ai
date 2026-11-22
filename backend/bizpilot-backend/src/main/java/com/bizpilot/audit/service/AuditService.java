package com.bizpilot.audit.service;

import com.bizpilot.audit.dto.AuditLogResponse;
import com.bizpilot.audit.entity.AuditLog;
import com.bizpilot.audit.repository.AuditLogRepository;
import com.bizpilot.auth.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(UUID ownerId, String action, String entityType, String entityId, String summary) {
        AuditLog entry = new AuditLog();
        entry.setOwner(userRepository.getReferenceById(ownerId));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setSummary(truncate(summary));
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(UUID ownerId, String entityType, Pageable pageable) {
        Page<AuditLog> page = (entityType != null && !entityType.isBlank())
                ? auditLogRepository.findByOwnerIdAndEntityTypeOrderByCreatedAtDesc(ownerId, entityType, pageable)
                : auditLogRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
        return page.map(AuditLogResponse::from);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_SUMMARY_LENGTH ? value : value.substring(0, MAX_SUMMARY_LENGTH);
    }
}
