package com.bizpilot.reminder.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizpilot.auth.entity.User;
import com.bizpilot.common.exception.BadRequestException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.invoice.entity.Invoice;
import com.bizpilot.invoice.entity.InvoiceStatus;
import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.entity.ReminderType;
import com.bizpilot.reminder.service.ReminderService;
import com.bizpilot.support.ControllerTestSupport;
import com.bizpilot.support.TestData;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ReminderController.class)
class ReminderControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ReminderService reminderService;

    private Reminder reminder(ReminderType type, ReminderStatus status) {
        User owner = TestData.user(ownerId);
        Invoice invoice = TestData.invoice(owner, TestData.customer(owner, "Acme"), "INV-1", InvoiceStatus.SENT,
                "100.00", "100.00", LocalDate.of(2026, 3, 1));
        Reminder r = new Reminder();
        r.setId(UUID.randomUUID());
        r.setOwner(owner);
        r.setInvoice(invoice);
        r.setReminderType(type);
        r.setStatus(status);
        r.setMessage("Hello Acme, this is a reminder that invoice INV-1 was due on 2026-03-01.");
        r.setReminderDate(LocalDate.of(2026, 3, 15));
        return r;
    }

    @Test
    void listReturnsReminderDtos() throws Exception {
        Reminder r = reminder(ReminderType.OVERDUE, ReminderStatus.OPEN);
        when(reminderService.list(ownerId, null)).thenReturn(List.of(r));

        mockMvc.perform(get("/api/reminders").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(r.getId().toString()))
                .andExpect(jsonPath("$.data[0].invoiceId").value(r.getInvoice().getId().toString()))
                .andExpect(jsonPath("$.data[0].reminderType").value("OVERDUE"))
                .andExpect(jsonPath("$.data[0].owner").doesNotExist())
                .andExpect(jsonPath("$.data[0].invoice").doesNotExist());
    }

    @Test
    void overdueUpcomingAndEmptyResults() throws Exception {
        when(reminderService.overdue(ownerId)).thenReturn(List.of(reminder(ReminderType.OVERDUE, ReminderStatus.OPEN)));
        when(reminderService.upcoming(ownerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/reminders/overdue").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/reminders/upcoming").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void generateReturns201() throws Exception {
        when(reminderService.generate(ownerId)).thenReturn(List.of(reminder(ReminderType.UPCOMING, ReminderStatus.OPEN)));

        mockMvc.perform(post("/api/reminders/generate").header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void updateStatusReturnsUpdatedReminder() throws Exception {
        Reminder r = reminder(ReminderType.OVERDUE, ReminderStatus.COMPLETED);
        when(reminderService.updateStatus(ownerId, r.getId(), ReminderStatus.COMPLETED)).thenReturn(r);

        mockMvc.perform(patch("/api/reminders/{id}/status", r.getId()).param("status", "COMPLETED")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void invalidOrMissingStatusIsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/reminders/{id}/status", id).param("status", "DONE").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/reminders/{id}/status", id).header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reminderService);
    }

    @Test
    void reopeningIsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(reminderService.updateStatus(ownerId, id, ReminderStatus.OPEN))
                .thenThrow(new BadRequestException("Reminder status must be COMPLETED or DISMISSED"));

        mockMvc.perform(patch("/api/reminders/{id}/status", id).param("status", "OPEN").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrForeignReminderIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(reminderService.updateStatus(ownerId, id, ReminderStatus.DISMISSED))
                .thenThrow(new NotFoundException("Reminder not found"));

        mockMvc.perform(patch("/api/reminders/{id}/status", id).param("status", "DISMISSED")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reminder not found"));
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reminders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reminders/generate")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reminderService);
    }

    @Test
    void corsPreflightForPatchFromFrontendIsAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/reminders/{id}/status", UUID.randomUUID())
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")));
        verifyNoInteractions(reminderService);
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/reminders")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
