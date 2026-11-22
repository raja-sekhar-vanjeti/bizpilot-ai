package com.bizpilot.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TOKEN = "jwt-token";
    private static final long EXPIRES_IN = 86400L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerWithNewEmailSavesHashedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest(
                "Ana Silva", "Ana Bakery", " Ana@Example.com ", "rawPassword123", "+351900000000");
        UUID userId = UUID.randomUUID();

        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword123")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(userId);
            return u;
        });
        when(jwtService.generateToken(userId)).thenReturn(TOKEN);
        when(jwtService.getExpirationSeconds()).thenReturn(EXPIRES_IN);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("encoded-hash", saved.getPasswordHash());
        assertNotEquals("rawPassword123", saved.getPasswordHash());
        assertEquals("ana@example.com", saved.getEmail());

        assertEquals(TOKEN, response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(EXPIRES_IN, response.expiresInSeconds());
    }

    @Test
    void registerWithExistingEmailThrowsConflictAndNeverSaves() {
        RegisterRequest request = new RegisterRequest(
                "Ana Silva", "Ana Bakery", "ana@example.com", "rawPassword123", null);

        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordThrowsUnauthorizedWithGenericMessage() {
        User user = userWithHash("ana@example.com", "stored-hash");
        LoginRequest request = new LoginRequest("ana@example.com", "wrongPassword");

        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "stored-hash")).thenReturn(false);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class, () -> authService.login(request));
        assertEquals("Invalid email or password", ex.getMessage());
        verify(jwtService, never()).generateToken(any(UUID.class));
    }

    @Test
    void loginWithUnknownEmailThrowsSameUnauthorizedMessage() {
        LoginRequest request = new LoginRequest("nobody@example.com", "whatever123");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class, () -> authService.login(request));
        assertEquals("Invalid email or password", ex.getMessage());
        verify(jwtService, never()).generateToken(any(UUID.class));
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        UUID userId = UUID.randomUUID();
        User user = userWithHash("ana@example.com", "stored-hash");
        user.setId(userId);
        LoginRequest request = new LoginRequest("ana@example.com", "rightPassword");

        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rightPassword", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken(userId)).thenReturn(TOKEN);
        when(jwtService.getExpirationSeconds()).thenReturn(EXPIRES_IN);

        AuthResponse response = authService.login(request);

        assertEquals(TOKEN, response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(EXPIRES_IN, response.expiresInSeconds());
    }

    @Test
    void meReturnsProfileWithMatchingEmail() {
        UUID userId = UUID.randomUUID();
        User user = userWithHash("ana@example.com", "stored-hash");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.me(userId);

        assertEquals(userId, profile.id());
        assertEquals("ana@example.com", profile.email());
        assertEquals("Ana Silva", profile.fullName());
        assertEquals("Ana Bakery", profile.businessName());
        boolean hasPasswordHashComponent = Arrays.stream(UserProfileResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name -> name.toLowerCase().contains("password"));
        assertFalse(hasPasswordHashComponent, "UserProfileResponse must not expose a password field");
    }

    @Test
    void meForUnknownIdThrowsNotFound() {
        UUID unknownId = UUID.randomUUID();

        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.me(unknownId));
    }

    private User userWithHash(String email, String passwordHash) {
        User user = new User();
        user.setFullName("Ana Silva");
        user.setBusinessName("Ana Bakery");
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
