package com.bizpilot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bizpilot.config.DatabaseConfigNormalizer.Resolved;
import org.junit.jupiter.api.Test;

class DatabaseConfigNormalizerTest {

    private static final String HOST = "ep-late-voice-bcoqsdzd-pooler.c-16.us-east-1.aws.neon.tech";
    private static final String SECRET = "npg_S3cr3tValue";

    @Test
    void plainJdbcUrlWithSeparateCredentialsIsKept() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "jdbc:postgresql://" + HOST + "/neondb?sslmode=require", "neondb_owner", SECRET);

        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", r.jdbcUrl());
        assertEquals("neondb_owner", r.username());
        assertEquals(SECRET, r.password());
        assertEquals(HOST, r.host());
        assertEquals("neondb", r.database());
        assertTrue(r.ssl());
        assertTrue(r.notes().isEmpty());
    }

    @Test
    void neonConnectionStringIsConvertedAndCredentialsMovedOutOfUrl() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "postgresql://neondb_owner:" + SECRET + "@" + HOST + "/neondb?sslmode=require&channel_binding=require",
                null, null);

        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", r.jdbcUrl());
        assertEquals("neondb_owner", r.username());
        assertEquals(SECRET, r.password());
        assertFalse(r.jdbcUrl().contains(SECRET));
    }

    @Test
    void jdbcPrefixedUrlWithEmbeddedCredentialsIsFixed() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "jdbc:postgresql://neondb_owner:" + SECRET + "@" + HOST + "/neondb?sslmode=require", null, null);

        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", r.jdbcUrl());
        assertEquals("neondb_owner", r.username());
        assertEquals(SECRET, r.password());
    }

    @Test
    void explicitCredentialsWinOverUrlCredentials() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "postgresql://other:wrong@" + HOST + "/neondb", "neondb_owner", SECRET);

        assertEquals("neondb_owner", r.username());
        assertEquals(SECRET, r.password());
        assertTrue(r.notes().stream().anyMatch(n -> n.contains("DB_PASSWORD differs")));
    }

    @Test
    void pastedWhitespaceNewlinesAndQuotesAreRemoved() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "  \"jdbc:postgresql://" + HOST + "/neondb?sslmode=require\"\n", " neondb_owner\n", SECRET + "\r\n");

        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", r.jdbcUrl());
        assertEquals("neondb_owner", r.username());
        assertEquals(SECRET, r.password());
    }

    @Test
    void sslmodeIsAddedForRemoteHostsOnly() {
        Resolved remote = DatabaseConfigNormalizer.resolve("jdbc:postgresql://" + HOST + "/neondb", "u", "p");
        Resolved local = DatabaseConfigNormalizer.resolve("jdbc:postgresql://localhost:5432/bizpilot_db", null, "p");

        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", remote.jdbcUrl());
        assertEquals("jdbc:postgresql://localhost:5432/bizpilot_db", local.jdbcUrl());
        assertFalse(local.ssl());
        assertNull(local.username());
    }

    @Test
    void urlEncodedPasswordIsDecodedAndPortKept() {
        Resolved r = DatabaseConfigNormalizer.resolve("postgres://user:p%40ss%2Bw@" + HOST + ":5432/neondb", null, null);

        assertEquals("p@ss+w", r.password());
        assertEquals("jdbc:postgresql://" + HOST + ":5432/neondb?sslmode=require", r.jdbcUrl());
    }

    @Test
    void remoteDatabaseWithoutUsernameFailsClearly() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseConfigNormalizer.resolve("jdbc:postgresql://" + HOST + "/neondb", "  ", SECRET));

        assertTrue(e.getMessage().contains("DB_USERNAME is not set"));
    }

    @Test
    void missingPasswordFailsClearly() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseConfigNormalizer.resolve("jdbc:postgresql://" + HOST + "/neondb", "neondb_owner", ""));

        assertTrue(e.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void missingDatabaseNameOrBadSchemeFailsClearly() {
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseConfigNormalizer.resolve("jdbc:postgresql://" + HOST, "u", "p"))
                .getMessage().contains("database name"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseConfigNormalizer.resolve("mysql://" + HOST + "/db", "u", "p"))
                .getMessage().contains("must start with"));
    }

    @Test
    void passwordNeverAppearsInMessagesOrNotes() {
        Resolved r = DatabaseConfigNormalizer.resolve(
                "postgresql://neondb_owner:" + SECRET + "@" + HOST + "/neondb?channel_binding=require", " x ", SECRET + " ");
        r.notes().forEach(n -> assertFalse(n.contains(SECRET), n));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DatabaseConfigNormalizer.resolve("mysql://neondb_owner:" + SECRET + "@" + HOST + "/db", null, null));
        assertFalse(e.getMessage().contains(SECRET), e.getMessage());
    }
}
