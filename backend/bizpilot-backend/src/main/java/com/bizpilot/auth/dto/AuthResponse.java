package com.bizpilot.auth.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds) {
}
