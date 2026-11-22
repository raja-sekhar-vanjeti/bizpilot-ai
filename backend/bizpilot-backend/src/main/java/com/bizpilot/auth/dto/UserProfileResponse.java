package com.bizpilot.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String fullName,
        String businessName,
        String email,
        String phone,
        LocalDateTime createdAt) {
}
