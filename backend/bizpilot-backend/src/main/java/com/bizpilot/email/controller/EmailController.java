package com.bizpilot.email.controller;

import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import com.bizpilot.email.dto.EmailRequest;
import com.bizpilot.email.dto.EmailResponse;
import com.bizpilot.email.service.EmailService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders/{id}/email")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<EmailRequest>> preview(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Email preview", emailService.preview(ownerId, id)));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<EmailResponse>> send(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Email processed", emailService.send(ownerId, id)));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<List<EmailResponse>>> deliveries(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID ownerId = currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok("Email deliveries", emailService.history(ownerId, id)));
    }

    private UUID currentUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }
}
