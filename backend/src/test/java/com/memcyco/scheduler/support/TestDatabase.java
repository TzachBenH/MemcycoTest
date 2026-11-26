package com.memcyco.scheduler.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;

/**
 * The PostgreSQL 15 database of the integration tests.
 * <ul>
 *     <li><b>Default</b>: a throwaway Testcontainers {@code postgres:15-alpine} container (needs Docker), started once
 *         and shared by all integration test classes.</li>
 *     <li><b>Without Docker</b>: point {@code IT_DB_URL} at an existing database, e.g.
 *         {@code IT_DB_URL=jdbc:postgresql://localhost:5432/scheduler_it ./mvnw verify}; optional {@code IT_DB_USER}
 *         and {@code IT_DB_PASSWORD} (default {@code scheduler}). The tests wipe that database, so its name must
 *         contain "test" or "_it".</li>
 * </ul>
 * Settings are read from environment variables or system properties with the same names.
 */
public final class TestDatabase {

    private static final String IMAGE = "postgres:15-alpine";
    private static final String DEFAULT_CREDENTIAL = "scheduler";

    private static PostgreSQLContainer<?> container;

    private TestDatabase() {
    }

    public static synchronized void register(DynamicPropertyRegistry registry) {
        String externalUrl = setting("IT_DB_URL");
        if (externalUrl != null) {
            requireDisposable(externalUrl);
            String user = settingOrDefault("IT_DB_USER");
            String password = settingOrDefault("IT_DB_PASSWORD");
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> user);
            registry.add("spring.datasource.password", () -> password);
            return;
        }
        if (container == null) {
            container = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
                    .withDatabaseName("scheduler_it")
                    .withUsername(DEFAULT_CREDENTIAL)
                    .withPassword(DEFAULT_CREDENTIAL);
            container.start(); // stopped by Testcontainers when the test JVM exits
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    private static void requireDisposable(String url) {
        String withoutQuery = url.split("\\?", 2)[0];
        String database = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (!database.contains("test") && !database.contains("_it")) {
            throw new IllegalStateException("IT_DB_URL must point to a disposable database whose name contains "
                    + "\"test\" or \"_it\", because the integration tests wipe it. Got: " + url);
        }
    }

    private static String settingOrDefault(String name) {
        String value = setting(name);
        return value == null ? DEFAULT_CREDENTIAL : value;
    }

    private static String setting(String name) {
        String value = System.getProperty(name, System.getenv(name));
        return value == null || value.isBlank() ? null : value.strip();
    }
}
