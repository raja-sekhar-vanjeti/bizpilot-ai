package com.bizpilot.reminder.service;

import com.bizpilot.audit.service.AuditService;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.entity.ReminderType;
import com.bizpilot.reminder.repository.ReminderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AuditService auditService;

    public ReminderService(
            ReminderRepository reminderRepository,
            InvoiceRepository invoiceRepository,
            UserRepository userRepository,
            Clock clock,
            AuditService auditService) {
        this.reminderRepository = reminderRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional
    public List<Reminder> generate(UUID ownerId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate upcomingLimit = today.plusDays(7);
        List<Reminder> created = new ArrayList<>();

        List<Invoice> invoices = invoiceRepository.findByOwnerIdAndStatusNotIn(
                ownerId, List.of(InvoiceStatus.DRAFT, InvoiceStatus.CANCELLED, InvoiceStatus.PAID));

        for (Invoice invoice : invoices) {
            BigDecimal outstanding = invoice.getOutstandingAmount();
            if (outstanding == null || outstanding.signum() <= 0) {
                continue;
            }
            LocalDate dueDate = invoice.getDueDate();
            ReminderType type;
            if (dueDate.isBefore(today)) {
                type = ReminderType.OVERDUE;
            } else if (!dueDate.isAfter(upcomingLimit)) {
                type = ReminderType.UPCOMING;
            } else {
                continue;
            }

            if (reminderRepository.existsByInvoiceIdAndReminderTypeAndStatus(
                    invoice.getId(), type, ReminderStatus.OPEN)) {
                continue;
            }
            // A reminder already handled (completed/dismissed) today must not reappear on regenerate.
            if (reminderRepository.existsByInvoiceIdAndReminderTypeAndReminderDate(
                    invoice.getId(), type, today)) {
                continue;
            }

            Reminder reminder = new Reminder();
            reminder.setOwner(invoice.getOwner());
            reminder.setInvoice(invoice);
            reminder.setReminderType(type);
            reminder.setStatus(ReminderStatus.OPEN);
            reminder.setReminderDate(today);
            reminder.setMessage(buildMessage(invoice, type));
            created.add(reminder);
        }

        List<Reminder> saved = reminderRepository.saveAll(created);
        for (Reminder reminder : saved) {
            auditService.record(ownerId, "CREATE", "REMINDER", String.valueOf(reminder.getId()),
                    "Generated " + reminder.getReminderType() + " reminder for invoice "
                            + reminder.getInvoice().getInvoiceNumber());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Reminder> list(UUID ownerId, ReminderStatus status) {
        ReminderStatus effective = status == null ? ReminderStatus.OPEN : status;
        return reminderRepository.findByOwnerIdAndStatusOrderByReminderDateAsc(ownerId, effective);
    }

    @Transactional(readOnly = true)
    public List<Reminder> overdue(UUID ownerId) {
        return list(ownerId, ReminderStatus.OPEN).stream()
                .filter(r -> r.getReminderType() == ReminderType.OVERDUE)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Reminder> upcoming(UUID ownerId) {
        return list(ownerId, ReminderStatus.OPEN).stream()
                .filter(r -> r.getReminderType() == ReminderType.UPCOMING)
                .toList();
    }

    @Transactional
    public Reminder updateStatus(UUID ownerId, UUID id, ReminderStatus status) {
        Reminder reminder = reminderRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Reminder not found"));
        if (status != ReminderStatus.COMPLETED && status != ReminderStatus.DISMISSED) {
            throw new BadRequestException("Reminder status must be COMPLETED or DISMISSED");
        }
        reminder.setStatus(status);
        Reminder saved = reminderRepository.save(reminder);
        auditService.record(ownerId, "UPDATE", "REMINDER", String.valueOf(saved.getId()),
                "Reminder marked " + status);
        return saved;
    }

    private String buildMessage(Invoice invoice, ReminderType type) {
        String customerName = invoice.getCustomer().getName();
        String currency = invoice.getCurrency();
        String amount = invoice.getOutstandingAmount().toPlainString();
        String opening = "Hello " + customerName + ", this is a reminder that invoice "
                + invoice.getInvoiceNumber();
        String closing = " Thank you.";
        if (type == ReminderType.OVERDUE) {
            return opening + " was due on " + invoice.getDueDate()
                    + " and has an outstanding balance of " + currency + " " + amount
                    + ". Please arrange payment at your earliest convenience." + closing;
        }
        return opening + " is due on " + invoice.getDueDate()
                + " and has an outstanding balance of " + currency + " " + amount
                + ". Please arrange payment by the due date." + closing;
    }
}
