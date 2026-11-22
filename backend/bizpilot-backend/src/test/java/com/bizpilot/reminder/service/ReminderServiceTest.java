package com.bizpilot.reminder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.customer.entity.Customer;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.invoice.repository.InvoiceRepository;
import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.entity.ReminderType;
import com.bizpilot.reminder.repository.ReminderRepository;
import com.bizpilot.support.TestData;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private UserRepository userRepository;

    private ReminderService service;
    private UUID ownerId;
    private User owner;
    private Customer customer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ReminderService(reminderRepository, invoiceRepository, userRepository, clock);
        ownerId = UUID.randomUUID();
        owner = TestData.user(ownerId);
        customer = TestData.customer(owner, "Acme Traders");
        when(reminderRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Invoice invoice(String number, String outstanding, LocalDate due) {
        return TestData.invoice(owner, customer, number, InvoiceStatus.SENT, "30000.00", outstanding, due);
    }

    private void openInvoices(Invoice... invoices) {
        when(invoiceRepository.findByOwnerIdAndStatusNotIn(eq(ownerId), any())).thenReturn(List.of(invoices));
    }

    private Reminder reminder(ReminderType type, ReminderStatus status) {
        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setOwner(owner);
        reminder.setInvoice(invoice("INV-9", "100.00", TODAY));
        reminder.setReminderType(type);
        reminder.setStatus(status);
        reminder.setMessage("m");
        reminder.setReminderDate(TODAY);
        return reminder;
    }

    @Test
    void generatesOverdueReminderWithFixedTemplate() {
        openInvoices(invoice("INV-1", "30000.00", LocalDate.of(2026, 3, 5)));

        List<Reminder> created = service.generate(ownerId);

        assertEquals(1, created.size());
        Reminder r = created.get(0);
        assertEquals(ReminderType.OVERDUE, r.getReminderType());
        assertEquals(ReminderStatus.OPEN, r.getStatus());
        assertEquals(TODAY, r.getReminderDate());
        assertSame(owner, r.getOwner());
        assertEquals("Hello Acme Traders, this is a reminder that invoice INV-1 was due on 2026-03-05 and has an "
                + "outstanding balance of INR 30000.00. Please arrange payment at your earliest convenience. "
                + "Thank you.", r.getMessage());
    }

    @Test
    void generatesUpcomingReminderOnlyWithinSevenDays() {
        openInvoices(
                invoice("INV-1", "1000.00", TODAY.plusDays(3)),
                invoice("INV-2", "1000.00", TODAY),
                invoice("INV-3", "1000.00", TODAY.plusDays(7)),
                invoice("INV-4", "1000.00", TODAY.plusDays(8)));

        List<Reminder> created = service.generate(ownerId);

        assertEquals(3, created.size());
        assertTrue(created.stream().allMatch(r -> r.getReminderType() == ReminderType.UPCOMING));
        assertEquals("Hello Acme Traders, this is a reminder that invoice INV-1 is due on 2026-03-18 and has an "
                + "outstanding balance of INR 1000.00. Please arrange payment by the due date. Thank you.",
                created.get(0).getMessage());
    }

    @Test
    void excludesDraftCancelledPaidAndZeroBalanceInvoices() {
        openInvoices(invoice("INV-1", "0.00", TODAY.minusDays(3)));

        assertTrue(service.generate(ownerId).isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<InvoiceStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(invoiceRepository).findByOwnerIdAndStatusNotIn(eq(ownerId), statuses.capture());
        assertEquals(Set.of(InvoiceStatus.DRAFT, InvoiceStatus.CANCELLED, InvoiceStatus.PAID),
                Set.copyOf(statuses.getValue()));
    }

    @Test
    void doesNotDuplicateAnOpenReminder() {
        Invoice inv = invoice("INV-1", "500.00", TODAY.minusDays(1));
        openInvoices(inv);
        when(reminderRepository.existsByInvoiceIdAndReminderTypeAndStatus(
                inv.getId(), ReminderType.OVERDUE, ReminderStatus.OPEN)).thenReturn(true);

        assertTrue(service.generate(ownerId).isEmpty());
    }

    @Test
    void doesNotRecreateAReminderHandledToday() {
        Invoice inv = invoice("INV-1", "500.00", TODAY.minusDays(1));
        openInvoices(inv);
        when(reminderRepository.existsByInvoiceIdAndReminderTypeAndReminderDate(
                inv.getId(), ReminderType.OVERDUE, TODAY)).thenReturn(true);

        assertTrue(service.generate(ownerId).isEmpty());
    }

    @Test
    void emptyBusinessGeneratesAndListsNothing() {
        assertTrue(service.generate(ownerId).isEmpty());
        assertTrue(service.list(ownerId, null).isEmpty());
        assertTrue(service.overdue(ownerId).isEmpty());
        assertTrue(service.upcoming(ownerId).isEmpty());
    }

    @Test
    void listDefaultsToOpenAndSplitsByType() {
        Reminder overdue = reminder(ReminderType.OVERDUE, ReminderStatus.OPEN);
        Reminder upcoming = reminder(ReminderType.UPCOMING, ReminderStatus.OPEN);
        when(reminderRepository.findByOwnerIdAndStatusOrderByReminderDateAsc(ownerId, ReminderStatus.OPEN))
                .thenReturn(List.of(overdue, upcoming));

        assertEquals(2, service.list(ownerId, null).size());
        assertEquals(List.of(overdue), service.overdue(ownerId));
        assertEquals(List.of(upcoming), service.upcoming(ownerId));
    }

    @Test
    void updateStatusCompletesOwnedReminder() {
        Reminder r = reminder(ReminderType.OVERDUE, ReminderStatus.OPEN);
        when(reminderRepository.findByIdAndOwnerId(r.getId(), ownerId)).thenReturn(Optional.of(r));
        when(reminderRepository.save(r)).thenReturn(r);

        Reminder updated = service.updateStatus(ownerId, r.getId(), ReminderStatus.COMPLETED);

        assertEquals(ReminderStatus.COMPLETED, updated.getStatus());
    }

    @Test
    void updateStatusToOpenIsRejected() {
        Reminder r = reminder(ReminderType.OVERDUE, ReminderStatus.DISMISSED);
        when(reminderRepository.findByIdAndOwnerId(r.getId(), ownerId)).thenReturn(Optional.of(r));

        assertThrows(BadRequestException.class, () -> service.updateStatus(ownerId, r.getId(), ReminderStatus.OPEN));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void anotherBusinessCannotUpdateOrSeeReminders() {
        Reminder r = reminder(ReminderType.OVERDUE, ReminderStatus.OPEN);
        UUID otherOwner = UUID.randomUUID();
        when(reminderRepository.findByIdAndOwnerId(r.getId(), ownerId)).thenReturn(Optional.of(r));
        when(reminderRepository.findByOwnerIdAndStatusOrderByReminderDateAsc(ownerId, ReminderStatus.OPEN))
                .thenReturn(List.of(r));
        openInvoices(invoice("INV-1", "500.00", TODAY.minusDays(1)));

        assertThrows(NotFoundException.class,
                () -> service.updateStatus(otherOwner, r.getId(), ReminderStatus.DISMISSED));
        assertTrue(service.list(otherOwner, null).isEmpty());
        assertTrue(service.generate(otherOwner).isEmpty());
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void serviceHasNoMessagingDependencies() {
        Constructor<?>[] constructors = ReminderService.class.getConstructors();
        assertEquals(1, constructors.length);
        assertEquals(List.of(ReminderRepository.class, InvoiceRepository.class, UserRepository.class, Clock.class),
                Arrays.asList(constructors[0].getParameterTypes()));
    }
}
