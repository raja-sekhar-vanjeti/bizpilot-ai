package com.bizpilot.reminder.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.reminder.entity.Reminder;
import com.bizpilot.reminder.entity.ReminderStatus;
import com.bizpilot.reminder.service.ReminderService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) ReminderStatus status) {
        UUID ownerId = currentUserId(authentication);
        List<ReminderResponse> reminders = reminderService.list(ownerId, status).stream()
                .map(ReminderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Reminders loaded", reminders));
    }

    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> overdue(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        List<ReminderResponse> reminders = reminderService.overdue(ownerId).stream()
                .map(ReminderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Overdue reminders", reminders));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> upcoming(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        List<ReminderResponse> reminders = reminderService.upcoming(ownerId).stream()
                .map(ReminderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Upcoming reminders", reminders));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> generate(Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        List<ReminderResponse> reminders = reminderService.generate(ownerId).stream()
                .map(ReminderResponse::from)
                .toList();
        return ResponseEntity.status(201).body(ApiResponse.ok("Reminders generated", reminders));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReminderResponse>> updateStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam ReminderStatus status) {
        UUID ownerId = currentUserId(authentication);
        Reminder updated = reminderService.updateStatus(ownerId, id, status);
        return ResponseEntity.ok(ApiResponse.ok("Reminder updated", ReminderResponse.from(updated)));
    }

    public record ReminderResponse(
            UUID id,
            UUID invoiceId,
            String reminderType,
            String status,
            String message,
            LocalDate reminderDate) {

        public static ReminderResponse from(Reminder r) {
            return new ReminderResponse(
                    r.getId(),
                    r.getInvoice().getId(),
                    r.getReminderType().name(),
                    r.getStatus().name(),
                    r.getMessage(),
                    r.getReminderDate());
        }
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
