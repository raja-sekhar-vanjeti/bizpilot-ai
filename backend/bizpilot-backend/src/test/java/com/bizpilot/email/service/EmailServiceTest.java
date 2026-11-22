package com.bizpilot.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
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
import com.bizpilot.reminder.entity.ReminderType;
import com.bizpilot.reminder.repository.ReminderRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private EmailDeliveryRepository deliveryRepository;

    private EmailService emailService;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID reminderId;
    private User owner;
    private Customer customer;
    private Invoice invoice;
    private Reminder reminder;
    private LocalDate dueDate;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(
                mailSender,
                reminderRepository,
                deliveryRepository,
                new EmailTemplateBuilder(),
                "sender@example.com");

        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        reminderId = UUID.randomUUID();
        dueDate = LocalDate.now().minusDays(10);

        owner = new User();
        owner.setId(ownerId);
        owner.setFullName("Owner Person");
        owner.setBusinessName("Studio Asha");
        owner.setEmail("owner@example.com");

        customer = new Customer();
        customer.setOwner(owner);
        customer.setName("Asha");
        customer.setEmail("asha@example.com");

        invoice = new Invoice();
        invoice.setOwner(owner);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("INV-TEST1");
        invoice.setDueDate(dueDate);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setTotalAmount(new BigDecimal("5000.00"));
        invoice.setOutstandingAmount(new BigDecimal("5000.00"));
        invoice.setCurrency("INR");

        reminder = new Reminder();
        reminder.setId(reminderId);
        reminder.setOwner(owner);
        reminder.setInvoice(invoice);
        reminder.setReminderType(ReminderType.OVERDUE);
        reminder.setStatus(ReminderStatus.OPEN);

        lenient().when(reminderRepository.findByIdAndOwnerId(reminderId, ownerId))
                .thenReturn(Optional.of(reminder));
        lenient().when(deliveryRepository.save(any(EmailDelivery.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendSuccessRecordsSentAndSendsOnce() {
        when(deliveryRepository.existsByReminderIdAndStatus(reminderId, EmailDeliveryStatus.SENT))
                .thenReturn(false);

        EmailResponse response = emailService.send(ownerId, reminderId);

        assertEquals("SENT", response.status());
        assertNotNull(response.sentAt());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void smtpAuthFailureIsSanitised() {
        when(deliveryRepository.existsByReminderIdAndStatus(reminderId, EmailDeliveryStatus.SENT))
                .thenReturn(false);
        doThrow(new MailAuthenticationException("secret-host-detail"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailResponse response = emailService.send(ownerId, reminderId);

        assertEquals("FAILED", response.status());
        assertEquals("Email sender authentication failed", response.failureReason());
        assertFalse(response.failureReason().contains("secret-host-detail"));
    }

    @Test
    void connectionFailureIsSanitised() {
        when(deliveryRepository.existsByReminderIdAndStatus(reminderId, EmailDeliveryStatus.SENT))
                .thenReturn(false);
        doThrow(new MailSendException("10.0.0.1 timed out"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailResponse response = emailService.send(ownerId, reminderId);

        assertEquals("FAILED", response.status());
        assertEquals("Mail server could not be reached. Try again later.", response.failureReason());
        assertFalse(response.failureReason().contains("10.0.0.1"));
    }

    @Test
    void invalidRecipientIsRejected() {
        customer.setEmail("not-an-email");

        assertThrows(BadRequestException.class, () -> emailService.preview(ownerId, reminderId));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void missingRecipientIsRejected() {
        customer.setEmail(null);

        assertThrows(BadRequestException.class, () -> emailService.send(ownerId, reminderId));

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void foreignReminderIsNotFound() {
        when(reminderRepository.findByIdAndOwnerId(reminderId, otherOwnerId))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> emailService.send(otherOwnerId, reminderId));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void duplicateSendIsPrevented() {
        when(deliveryRepository.existsByReminderIdAndStatus(reminderId, EmailDeliveryStatus.SENT))
                .thenReturn(true);

        assertThrows(BadRequestException.class, () -> emailService.send(ownerId, reminderId));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void notOpenReminderIsRejected() {
        reminder.setStatus(ReminderStatus.COMPLETED);

        assertThrows(ConflictException.class, () -> emailService.send(ownerId, reminderId));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void paidInvoiceIsRejected() {
        invoice.setStatus(InvoiceStatus.PAID);

        assertThrows(ConflictException.class, () -> emailService.send(ownerId, reminderId));
    }

    @Test
    void contentIsEscaped() {
        customer.setName("<script>alert(1)</script> & Co");

        EmailRequest request = emailService.preview(ownerId, reminderId);

        assertFalse(request.htmlBody().contains("<script>"));
        assertTrue(request.htmlBody().contains("&lt;script&gt;"));
        assertTrue(request.htmlBody().contains("&amp; Co"));
    }

    @Test
    void contentCorrect() {
        EmailRequest request = emailService.preview(ownerId, reminderId);

        assertEquals("Payment Reminder \u2013 Invoice INV-TEST1", request.subject());
        // The template puts each label and its value in separate table cells, so the
        // label and the amount / date are checked as separate fragments.
        assertTrue(request.htmlBody().contains("Amount Outstanding:"));
        assertTrue(request.htmlBody().contains("INR 5000.00"));
        assertTrue(request.htmlBody().contains("Due Date:"));
        assertTrue(request.htmlBody().contains(dueDate.toString()));
    }
}
