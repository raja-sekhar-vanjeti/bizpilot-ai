package com.bizpilot.audit.repository;

import com.bizpilot.audit.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Page<AuditLog> findByOwnerIdAndEntityTypeOrderByCreatedAtDesc(UUID ownerId, String entityType, Pageable pageable);
}
