package com.memcyco.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Execution engine settings ({@code app.execution.*}). The retry policy is intentionally <b>not</b> here: it is
 * fixed in code (see {@code service.RetryPolicy}).
 *
 * @param stuckTimeout       a RUNNING execution without a heartbeat for longer than this is marked STUCK
 * @param stuckCheckInterval how often the stuck-execution monitor runs
 * @param heartbeatThrottle  a task's heartbeats are written to the database at most this often
 * @param retention          execution history older than this is deleted
 * @param retentionCron      when the history cleanup runs (Quartz cron, UTC)
 */
@ConfigurationProperties(prefix = "app.execution")
public record ExecutionProperties(@DefaultValue("5m") Duration stuckTimeout,
                                  @DefaultValue("1m") Duration stuckCheckInterval,
                                  @DefaultValue("10s") Duration heartbeatThrottle,
                                  @DefaultValue("30d") Duration retention,
                                  @DefaultValue("0 0 3 * * ?") String retentionCron) {
}
