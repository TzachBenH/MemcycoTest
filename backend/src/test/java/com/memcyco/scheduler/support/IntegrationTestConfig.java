package com.memcyco.scheduler.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only beans of the (single, shared) integration test application context. */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    /** Joins the real tasks in the TaskRegistry, so the engine can be driven into every path. */
    @Bean
    ControllableTask controllableTask() {
        return new ControllableTask();
    }

    /** Replaces the production policy's 10s / 20s delays. */
    @Bean
    @Primary
    FastRetryPolicy fastRetryPolicy() {
        return new FastRetryPolicy();
    }

    /** Every test run starts from an empty schema (matters when IT_DB_URL points at a reused database). */
    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
