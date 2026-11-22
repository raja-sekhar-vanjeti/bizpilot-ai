package com.bizpilot.reminder.repository;

import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.entity.ReminderType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByOwnerIdAndStatusOrderByReminderDateAsc(UUID ownerId, ReminderStatus status);

    Optional<Reminder> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByInvoiceIdAndReminderTypeAndStatus(UUID invoiceId, ReminderType reminderType, ReminderStatus status);

    boolean existsByInvoiceIdAndReminderTypeAndReminderDate(UUID invoiceId, ReminderType reminderType, LocalDate reminderDate);
}
