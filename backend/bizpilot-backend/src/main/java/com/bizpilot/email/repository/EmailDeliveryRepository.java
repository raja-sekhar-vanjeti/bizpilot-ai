package com.bizpilot.email.repository;

import com.bizpilot.email.entity.EmailDelivery;
import com.bizpilot.email.entity.EmailDeliveryStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    boolean existsByReminderIdAndStatus(UUID reminderId, EmailDeliveryStatus status);

    List<EmailDelivery> findByReminderIdOrderByCreatedAtDesc(UUID reminderId);
}
