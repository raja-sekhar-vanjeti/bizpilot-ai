package com.bizpilot.email.service;

import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.email.dto.EmailRequest;
import com.bizpilot.email.dto.EmailResponse;
import com.bizpilot.email.entity.EmailDelivery;
import com.bizpilot.email.entity.EmailDeliveryStatus;
import com.bizpilot.email.repository.EmailDeliveryRepository;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.repository.ReminderRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_RECIPIENT_LENGTH = 255;

    private final JavaMailSender mailSender;
    private final ReminderRepository reminderRepository;
    private final EmailDeliveryRepository deliveryRepository;
    private final EmailTemplateBuilder templateBuilder;
    private final String fromAddress;

    public EmailService(
            JavaMailSender mailSender,
            ReminderRepository reminderRepository,
            EmailDeliveryRepository deliveryRepository,
            EmailTemplateBuilder templateBuilder,
            @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.reminderRepository = reminderRepository;
        this.deliveryRepository = deliveryRepository;
        this.templateBuilder = templateBuilder;
        this.fromAddress = fromAddress;
    }

    @Transactional(readOnly = true)
    public EmailRequest preview(UUID ownerId, UUID reminderId) {
        Reminder reminder = loadOwnedReminder(ownerId, reminderId);
        return buildRequest(reminder);
    }

    @Transactional
    public EmailResponse send(UUID ownerId, UUID reminderId) {
        Reminder reminder = loadOwnedReminder(ownerId, reminderId);

        if (reminder.getStatus() != ReminderStatus.OPEN) {
            throw new ConflictException("Only open reminders can be emailed");
        }
        Invoice invoice = reminder.getInvoice();
        if (invoice.getStatus() == InvoiceStatus.DRAFT
                || invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ConflictException("This invoice is not awaiting payment");
        }
        if (invoice.getOutstandingAmount() == null || invoice.getOutstandingAmount().signum() <= 0) {
            throw new ConflictException("This invoice has no outstanding balance");
        }

        if (deliveryRepository.existsByReminderIdAndStatus(reminderId, EmailDeliveryStatus.SENT)) {
            throw new BadRequestException("A payment reminder email has already been sent for this reminder");
        }

        EmailRequest request = buildRequest(reminder);

        EmailDelivery delivery = new EmailDelivery();
        delivery.setOwner(reminder.getOwner());
        delivery.setReminder(reminder);
        delivery.setRecipientEmail(request.to());
        delivery.setSubject(request.subject());
        delivery.setStatus(EmailDeliveryStatus.PENDING);
        delivery = deliveryRepository.save(delivery);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(request.htmlBody(), true);
            mailSender.send(message);
            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setSentAt(LocalDateTime.now());
        } catch (MailAuthenticationException ex) {
            log.warn("Email send failed: {}", ex.getClass().getSimpleName());
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setFailureReason("Email sender authentication failed");
        } catch (MailException ex) {
            log.warn("Email send failed: {}", ex.getClass().getSimpleName());
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setFailureReason("Mail server could not be reached. Try again later.");
        } catch (MessagingException ex) {
            log.warn("Email send failed: {}", ex.getClass().getSimpleName());
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setFailureReason("Email could not be prepared. Try again later.");
        }

        return toResponse(deliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public List<EmailResponse> history(UUID ownerId, UUID reminderId) {
        loadOwnedReminder(ownerId, reminderId);
        return deliveryRepository.findByReminderIdOrderByCreatedAtDesc(reminderId).stream()
                .map(EmailService::toResponse)
                .toList();
    }

    private Reminder loadOwnedReminder(UUID ownerId, UUID reminderId) {
        return reminderRepository.findByIdAndOwnerId(reminderId, ownerId)
                .orElseThrow(() -> new NotFoundException("Reminder not found"));
    }

    private EmailRequest buildRequest(Reminder reminder) {
        Invoice invoice = reminder.getInvoice();
        Customer customer = invoice.getCustomer();
        String recipient = customer.getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("Customer has no email address");
        }
        if (recipient.length() > MAX_RECIPIENT_LENGTH || !EMAIL_PATTERN.matcher(recipient).matches()) {
            throw new BadRequestException("Customer email address is invalid");
        }
        return templateBuilder.buildPaymentReminder(
                recipient,
                customer.getName(),
                invoice.getInvoiceNumber(),
                invoice.getTotalAmount(),
                invoice.getOutstandingAmount(),
                invoice.getCurrency(),
                invoice.getDueDate(),
                reminder.getOwner().getBusinessName());
    }


    private static EmailResponse toResponse(EmailDelivery d) {
        return new EmailResponse(
                d.getId(),
                d.getReminder().getId(),
                d.getRecipientEmail(),
                d.getSubject(),
                d.getStatus().name(),
                d.getSentAt(),
                d.getFailureReason());
    }
}
