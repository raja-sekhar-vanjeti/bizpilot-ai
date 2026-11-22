package com.bizpilot.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Runs before the datasource is created. When DB_URL is set (hosted deployments), it normalizes the
 * URL and credentials via {@link DatabaseConfigNormalizer}, overrides the spring.datasource.* values
 * and prints a one-line summary without the password. Invalid configuration stops startup with a
 * clear message instead of a driver stack trace. Without DB_URL, application.properties defaults
 * (local PostgreSQL) apply unchanged.
 */
public class DatabaseEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String SOURCE_NAME = "bizpilotNormalizedDatabase";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        String rawUrl = env.getProperty("DB_URL");
        if (DatabaseConfigNormalizer.clean(rawUrl) == null) {
            return;
        }

        DatabaseConfigNormalizer.Resolved db;
        try {
            db = DatabaseConfigNormalizer.resolve(rawUrl, env.getProperty("DB_USERNAME"), env.getProperty("DB_PASSWORD"));
        } catch (IllegalStateException e) {
            System.err.println("[BizPilot] DATABASE CONFIGURATION ERROR: " + e.getMessage());
            throw e;
        }

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", db.jdbcUrl());
        props.put("spring.datasource.password", db.password());
        if (db.username() != null) {
            props.put("spring.datasource.username", db.username());
        }
        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));

        System.out.println("[BizPilot] Database: host=" + db.host() + ", database=" + db.database()
                + ", user=" + (db.username() != null ? db.username() : "(default)")
                + ", ssl=" + db.ssl() + ", password=set");
        db.notes().forEach(note -> System.out.println("[BizPilot] Database config: " + note));
    }
}
