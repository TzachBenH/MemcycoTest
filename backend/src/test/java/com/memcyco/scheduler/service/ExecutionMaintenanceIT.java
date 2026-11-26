package com.memcyco.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.memcyco.scheduler.job.SystemJobsInitializer;
import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static com.memcyco.scheduler.support.Requests.logScheduling;
import static com.memcyco.scheduler.support.Requests.yearly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The internal system jobs: the stuck-execution monitor and history retention. */
class ExecutionMaintenanceIT extends IntegrationTest {

    @Autowired
    private ExecutionMaintenanceService maintenance;
    @Autowired
    private FingerprintService fingerprints;

    @Test
    void aRunningExecutionWithoutAHeartbeatForFiveMinutesBecomesStuck() throws Exception {
        UUID id = createId(logScheduling("Monitored", "x", yearly(3)));
        UUID silent = insertExecution(id, "RUNNING", "run-key-1", Duration.ofMinutes(10), Duration.ofMinutes(6));
        UUID alive = insertExecution(id, "RUNNING", "run-key-2", Duration.ofMinutes(10), Duration.ofSeconds(20));

        assertThat(maintenance.markStuckExecutions()).isOne();

        Execution stuck = execution(id, silent);
        assertThat(stuck.status()).isEqualTo("STUCK");
        assertThat(stuck.errorMessage()).isEqualTo("No heartbeat for more than 5 minutes");
        assertThat(stuck.finishedAt()).isNotNull();
        assertThat(execution(id, alive).status()).isEqualTo("RUNNING");
        assertThat(maintenance.markStuckExecutions()).isZero();
    }

    @Test
    void markingARunStuckReleasesItsRuntimeLock() throws Exception {
        UUID id = createId(logScheduling("Crashed", "x", yearly(4)));
        // A run left behind by a crashed instance: RUNNING with the scheduling's real run key, no heartbeat since.
        Map<String, Object> parameters = objectMapper.convertValue(fetch(id).get("parameters"), new TypeReference<>() {
        });
        insertExecution(id, "RUNNING", fingerprints.runKey("LOG", parameters), Duration.ofMinutes(20),
                Duration.ofMinutes(20));

        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).contains("SKIPPED"));

        maintenance.markStuckExecutions();
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).contains("SUCCESS"));
        assertThat(statuses(id)).containsExactlyInAnyOrder("STUCK", "SKIPPED", "SUCCESS");
    }

    @Test
    void retentionDeletesOnlyOldFinishedHistoryAndExpiredEditLocks() throws Exception {
        UUID id = createId(logScheduling("Old history", "x", yearly(5)));
        UUID other = createId(logScheduling("Locked", "y", yearly(6)));
        UUID oldSuccess = insertExecution(id, "SUCCESS", "k1", Duration.ofDays(40), Duration.ofDays(40));
        UUID oldRunning = insertExecution(id, "RUNNING", "k2", Duration.ofDays(40), Duration.ofSeconds(5));
        UUID recentFailure = insertExecution(id, "FAILED", "k3", Duration.ofDays(1), Duration.ofDays(1));
        acquireLock(id, "tab-A");
        acquireLock(other, "tab-A");
        jdbc.update("UPDATE scheduling_edit_locks SET expires_at = now() - interval '1 minute' WHERE scheduling_id = ?",
                id);

        assertThat(maintenance.purgeOldHistory()).isOne();

        assertThat(executions(id)).extracting(Execution::id)
                .containsExactlyInAnyOrder(oldRunning, recentFailure)
                .doesNotContain(oldSuccess);
        assertThat(jdbc.queryForList("SELECT scheduling_id FROM scheduling_edit_locks", UUID.class))
                .containsExactly(other);
    }

    @Test
    void theSystemJobsAreRegisteredAndRunThroughQuartz() throws Exception {
        JobKey monitor = JobKey.jobKey("stuck-execution-monitor", SystemJobsInitializer.SYSTEM_GROUP);
        JobKey retention = JobKey.jobKey("history-retention", SystemJobsInitializer.SYSTEM_GROUP);
        assertThat(scheduler.checkExists(monitor)).isTrue();
        assertThat(scheduler.checkExists(retention)).isTrue();
        CronTrigger retentionTrigger = (CronTrigger) scheduler.getTrigger(
                TriggerKey.triggerKey("history-retention", SystemJobsInitializer.SYSTEM_GROUP));
        assertThat(retentionTrigger.getCronExpression()).isEqualTo("0 0 3 * * ?");
        assertThat(retentionTrigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone("UTC"));

        UUID id = createId(logScheduling("Monitored by Quartz", "x", yearly(7)));
        UUID silent = insertExecution(id, "RUNNING", "k1", Duration.ofMinutes(10), Duration.ofMinutes(10));
        UUID old = insertExecution(id, "SUCCESS", "k2", Duration.ofDays(31), Duration.ofDays(31));

        inTransaction(() -> scheduler.triggerJob(monitor));
        await().atMost(TIMEOUT).until(() -> execution(id, silent).status().equals("STUCK"));
        inTransaction(() -> scheduler.triggerJob(retention));
        await().atMost(TIMEOUT).until(() -> executions(id).stream().noneMatch(e -> e.id().equals(old)));
    }

    @Test
    void describesDurationsForHumans() {
        assertThat(ExecutionMaintenanceService.describe(Duration.ofDays(30))).isEqualTo("30 days");
        assertThat(ExecutionMaintenanceService.describe(Duration.ofMinutes(5))).isEqualTo("5 minutes");
        assertThat(ExecutionMaintenanceService.describe(Duration.ofSeconds(90))).isEqualTo("90 seconds");
    }

    private Execution execution(UUID schedulingId, UUID executionId) {
        return executions(schedulingId).stream().filter(e -> e.id().equals(executionId)).findFirst().orElseThrow();
    }

    private UUID insertExecution(UUID schedulingId, String status, String runKey, Duration startedAgo,
                                 Duration lastHeartbeatAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO task_executions (id, scheduling_id, task_key, parameters, run_key, status, trigger_type,
                                             attempt, executed_by, started_at, last_heartbeat_at, finished_at)
                VALUES (?, ?, 'LOG', '{}'::jsonb, ?, ?, 'SCHEDULED', 1, 'crashed-node',
                        now() - ? * interval '1 second', now() - ? * interval '1 second',
                        CASE WHEN ? = 'RUNNING' THEN NULL ELSE now() - ? * interval '1 second' END)
                """, id, schedulingId, runKey, status, startedAgo.toSeconds(), lastHeartbeatAgo.toSeconds(), status,
                startedAgo.toSeconds());
        return id;
    }
}
