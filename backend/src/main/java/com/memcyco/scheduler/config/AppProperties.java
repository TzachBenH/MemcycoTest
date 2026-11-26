package com.memcyco.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application-specific settings ({@code app.*} in application.yml).
 *
 * @param instanceId id of this backend instance: Quartz cluster node id and {@code task_executions.executed_by}
 * @param cors       CORS settings for browsers calling the API directly (local dev UI, Swagger)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String instanceId, Cors cors) {

    public record Cors(List<String> allowedOrigins) {
    }
}
