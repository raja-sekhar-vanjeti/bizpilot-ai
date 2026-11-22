package com.bizpilot.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the DB_URL / DB_USERNAME / DB_PASSWORD environment values into a JDBC URL and credentials
 * the PostgreSQL driver accepts.
 *
 * <p>Hosted providers such as Neon hand out URLs like
 * {@code postgresql://user:password@host/db?sslmode=require&channel_binding=require}. The pgJDBC driver
 * does not understand credentials inside the URL (it treats {@code user:password@host} as the host
 * name), so they are moved into the username/password properties here. Values pasted into a hosting
 * dashboard often carry stray whitespace or quotes; those are removed too.
 *
 * <p>Error messages and notes never contain the password.
 */
public final class DatabaseConfigNormalizer {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    /** Accepted by libpq but ignored by pgJDBC; dropped to avoid confusion. */
    private static final Set<String> IGNORED_PARAMS = Set.of("channel_binding");

    private DatabaseConfigNormalizer() {
    }

    /**
     * @param username null when the configured default should be kept (local databases only)
     */
    public record Resolved(String jdbcUrl, String username, String password, String host, String database,
                           boolean ssl, List<String> notes) {
    }

    public static Resolved resolve(String rawUrl, String rawUsername, String rawPassword) {
        String url = clean(rawUrl);
        if (url == null) {
            throw new IllegalStateException("DB_URL is empty.");
        }
        List<String> notes = new ArrayList<>();

        String rest = url;
        if (rest.regionMatches(true, 0, "jdbc:", 0, 5)) {
            rest = rest.substring(5);
        } else {
            notes.add("DB_URL had no 'jdbc:' prefix; converted to a JDBC URL.");
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("postgresql://")) {
            rest = rest.substring("postgresql://".length());
        } else if (lower.startsWith("postgres://")) {
            rest = rest.substring("postgres://".length());
        } else {
            throw new IllegalStateException(
                    "DB_URL must start with jdbc:postgresql:// or postgresql:// (got a value starting with '"
                            + safePrefix(url) + "').");
        }

        int queryStart = rest.indexOf('?');
        String query = queryStart >= 0 ? rest.substring(queryStart + 1) : "";
        String beforeQuery = queryStart >= 0 ? rest.substring(0, queryStart) : rest;
        int slash = beforeQuery.indexOf('/');
        String authority = slash >= 0 ? beforeQuery.substring(0, slash) : beforeQuery;
        String database = slash >= 0 ? beforeQuery.substring(slash + 1) : "";

        String urlUser = null;
        String urlPassword = null;
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            String userInfo = authority.substring(0, at);
            authority = authority.substring(at + 1);
            int colon = userInfo.indexOf(':');
            urlUser = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            urlPassword = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
            notes.add("Credentials found inside DB_URL were moved out of the URL (the PostgreSQL JDBC driver "
                    + "does not support user:password@host).");
        }

        if (authority.isBlank()) {
            throw new IllegalStateException("DB_URL is missing the database host.");
        }
        if (database.isBlank()) {
            throw new IllegalStateException("DB_URL is missing the database name (expected .../<database>).");
        }
        String host = hostOf(authority);
        boolean local = LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));

        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            if (IGNORED_PARAMS.contains(key)) {
                notes.add("Ignored URL parameter '" + key + "' (not used by the PostgreSQL JDBC driver).");
                continue;
            }
            params.put(key, value);
        }
        if (!local && !params.containsKey("sslmode") && !params.containsKey("ssl")) {
            params.put("sslmode", "require");
            notes.add("Added sslmode=require for the remote database.");
        }
        boolean ssl = !"disable".equalsIgnoreCase(params.getOrDefault("sslmode", local ? "disable" : "require"));

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(authority).append('/').append(database);
        if (!params.isEmpty()) {
            StringBuilder q = new StringBuilder();
            params.forEach((k, v) -> q.append(q.isEmpty() ? "" : "&").append(k).append(v.isEmpty() ? "" : "=" + v));
            jdbc.append('?').append(q);
        }

        String explicitUser = clean(rawUsername);
        String explicitPassword = clean(rawPassword);
        if (explicitUser != null && urlUser != null && !explicitUser.equals(urlUser)) {
            notes.add("DB_USERNAME differs from the user inside DB_URL; using DB_USERNAME.");
        }
        if (explicitPassword != null && urlPassword != null && !explicitPassword.equals(urlPassword)) {
            notes.add("DB_PASSWORD differs from the password inside DB_URL; using DB_PASSWORD.");
        }
        String username = explicitUser != null ? explicitUser : urlUser;
        String password = explicitPassword != null ? explicitPassword : urlPassword;

        if (username == null && !local) {
            throw new IllegalStateException("DB_USERNAME is not set. The default user 'postgres' only applies to a "
                    + "local database; set DB_USERNAME (for Neon, e.g. neondb_owner) or include it in DB_URL.");
        }
        if (password == null) {
            throw new IllegalStateException("DB_PASSWORD is not set or is empty.");
        }
        if (rawPassword != null && explicitPassword != null && !rawPassword.equals(explicitPassword)) {
            notes.add("Removed surrounding whitespace or quotes from DB_PASSWORD.");
        }
        if (rawUsername != null && explicitUser != null && !rawUsername.equals(explicitUser)) {
            notes.add("Removed surrounding whitespace or quotes from DB_USERNAME.");
        }

        return new Resolved(jdbc.toString(), username, password, host, database, ssl, List.copyOf(notes));
    }

    /** Trims whitespace (including pasted newlines) and one pair of surrounding quotes; blank becomes null. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1).strip();
        }
        return v.isEmpty() ? null : v;
    }

    private static String hostOf(String authority) {
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            return end > 0 ? authority.substring(0, end + 1) : authority;
        }
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String safePrefix(String url) {
        int end = Math.min(url.length(), 12);
        int at = url.indexOf('@');
        if (at >= 0 && at < end) {
            end = at;
        }
        int colon = url.indexOf(':', url.indexOf("//") + 2);
        if (url.contains("//") && colon > 0 && colon < end) {
            end = colon;
        }
        return url.substring(0, end);
    }
}
