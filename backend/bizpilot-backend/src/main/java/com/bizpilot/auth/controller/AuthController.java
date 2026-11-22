package com.bizpilot.auth.controller;

import com.bizpilot.auth.dto.AuthResponse;
import com.bizpilot.auth.dto.LoginRequest;
import com.bizpilot.auth.dto.RegisterRequest;
import com.bizpilot.auth.dto.UserProfileResponse;
import com.bizpilot.auth.service.AuthService;
import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.ok("User registered", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new UnauthorizedException("Authentication required");
        }
        UserProfileResponse profile = authService.me(userId);
        return ResponseEntity.ok(ApiResponse.ok("Profile loaded", profile));
    }
}
