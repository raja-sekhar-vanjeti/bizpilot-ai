package com.bizpilot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    // Test-only secret literal; not a real credential.
    private static final String SECRET = "test-only-secret-key-for-unit-tests-32chars";
    private static final String OTHER_SECRET = "another-test-only-secret-key-for-unit-tests";
    private static final long EXPIRATION_MS = 3_600_000L;

    @Test
    void generateTokenThenExtractUserIdReturnsSameUuid() {
        JwtService service = new JwtService(SECRET, EXPIRATION_MS);
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId);

        assertEquals(userId, service.extractUserId(token));
    }

    @Test
    void isValidReturnsTrueForFreshToken() {
        JwtService service = new JwtService(SECRET, EXPIRATION_MS);

        String token = service.generateToken(UUID.randomUUID());

        assertTrue(service.isValid(token));
    }

    @Test
    void tokenSignedWithDifferentSecretIsNotValid() {
        JwtService issuer = new JwtService(OTHER_SECRET, EXPIRATION_MS);
        JwtService verifier = new JwtService(SECRET, EXPIRATION_MS);

        String foreignToken = issuer.generateToken(UUID.randomUUID());

        assertFalse(verifier.isValid(foreignToken));
    }

    @Test
    void isValidReturnsFalseForGarbage() {
        JwtService service = new JwtService(SECRET, EXPIRATION_MS);

        assertFalse(service.isValid("garbage"));
    }

    @Test
    void constructorRejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("0123456789", EXPIRATION_MS));
    }
}
