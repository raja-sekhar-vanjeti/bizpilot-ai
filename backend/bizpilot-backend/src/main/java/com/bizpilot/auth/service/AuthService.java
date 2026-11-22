package com.bizpilot.auth.service;

import com.bizpilot.auth.dto.AuthResponse;
import com.bizpilot.auth.dto.LoginRequest;
import com.bizpilot.auth.dto.RegisterRequest;
import com.bizpilot.auth.dto.UserProfileResponse;
import com.bizpilot.auth.entity.User;
import com.bizpilot.auth.repository.UserRepository;
import com.bizpilot.common.exception.ConflictException;
import com.bizpilot.common.exception.NotFoundException;
import com.bizpilot.common.exception.UnauthorizedException;
import com.bizpilot.security.JwtService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setBusinessName(request.businessName());
        user.setPhone(request.phone());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        return buildAuthResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return buildAuthResponse(user.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getBusinessName(),
                user.getEmail(),
                user.getPhone(),
                user.getCreatedAt());
    }

    private AuthResponse buildAuthResponse(UUID userId) {
        return new AuthResponse(jwtService.generateToken(userId), "Bearer", jwtService.getExpirationSeconds());
    }

    private String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
