package com.bizpilot.email.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailResponse(
        UUID deliveryId,
        UUID reminderId,
        String recipientEmail,
        String subject,
        String status,
        LocalDateTime sentAt,
        String failureReason) {}
