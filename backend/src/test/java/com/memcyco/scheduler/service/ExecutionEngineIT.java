package com.memcyco.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.memcyco.scheduler.support.ControllableTask.Scenario.BLOCK;
import static com.memcyco.scheduler.support.ControllableTask.Scenario.FAIL;
import static com.memcyco.scheduler.support.ControllableTask.Scenario.FAIL_FIRST_ATTEMPT;
import static com.memcyco.scheduler.support.ControllableTask.Scenario.FAIL_PERMANENTLY;
import static com.memcyco.scheduler.support.Requests.controllable;
import static com.memcyco.scheduler.support.Requests.interval;
import static com.memcyco.scheduler.support.Requests.logScheduling;
import static com.memcyco.scheduler.support.Requests.oneTime;
import static com.memcyco.scheduler.support.Requests.yearly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The execution engine end to end, with the real clustered Quartz scheduler: triggers fire, the runtime lock skips
 * overlaps, failures are retried by durable one-shot triggers, pending retries are cancelled, heartbeats stop a run
 * that was declared stuck.
 */
class ExecutionEngineIT extends IntegrationTest {

    @Autowired
    private QuartzReconciler reconciler;

    @Test
    void runNowRecordsASuccessfulManualRun() throws Exception {
        UUID id = createId(logScheduling("On demand", "hello", yearly(3)));

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));
        Execution run = executions(id).getFirst();
        assertThat(run.triggerType()).isEqualTo("MANUAL");
        assertThat(run.attempt()).isOne();
        assertThat(run.executedBy()).isEqualTo(INSTANCE_ID);
        assertThat(run.finishedAt()).isAfterOrEqualTo(run.startedAt());
        assertThat(run.nextRetryAt()).isNull();
        assertThat(run.errorMessage()).isNull();
    }

    @Test
    void aPausedSchedulingCanStillBeRunNow() throws Exception {
        UUID id = createId(logScheduling("Paused but runnable", "hello", yearly(4)));
        pause(id);

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));
        assertThat(fetch(id).get("status").asText()).isEqualTo("PAUSED");
    }

    @Test
    void aOneTimeSchedulingRunsOnceAndThenIsCompleted() throws Exception {
        Instant runAt = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        UUID id = createId(logScheduling("Once", "only once", oneTime(runAt)));

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));
        Execution run = executions(id).getFirst();
        assertThat(run.triggerType()).isEqualTo("SCHEDULED");
        assertThat(run.startedAt()).isAfterOrEqualTo(runAt.minusMillis(500));

        JsonNode scheduling = fetch(id);
        assertThat(scheduling.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(scheduling.get("nextFireTime").isNull()).isTrue();
        assertThat(scheduling.get("lastExecution").get("status").asText()).isEqualTo("SUCCESS");
        mvc.perform(get("/api/schedulings").param("status", "COMPLETED"))
                .andExpect(jsonPath("$.content[*].id").value(contains(id.toString())));
        mvc.perform(get("/api/schedulings").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(id.toString()))));
        // A fired one-time trigger is not "missing": reconciliation leaves it alone.
        assertThat(reconciler.reconcile().recreated()).isZero();
    }

    @Test
    void anIntervalSchedulingStartsNowAndRepeats() throws Exception {
        UUID id = createId(logScheduling("Every minute", "tick", interval(1, "MINUTES")));

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));
        assertThat(executions(id).getFirst().triggerType()).isEqualTo("SCHEDULED");
        Instant next = Instant.parse(fetch(id).get("nextFireTime").asText());
        assertThat(next).isBetween(Instant.now().plusSeconds(30), Instant.now().plusSeconds(61));
    }

    @Test
    void aFailingRunIsRetriedUntilThreeAttemptsFailed() throws Exception {
        UUID id = createId(controllable("Always failing", FAIL, "retry-chain", yearly(5)));

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED", "FAILED", "FAILED")));
        List<Execution> runs = executions(id);
        assertThat(runs).extracting(Execution::attempt).containsExactly(1, 2, 3);
        assertThat(runs).extracting(Execution::triggerType).containsExactly("MANUAL", "RETRY", "RETRY");
        assertThat(runs.get(0).previousAttemptId()).isNull();
        assertThat(runs.get(1).previousAttemptId()).isEqualTo(runs.get(0).id());
        assertThat(runs.get(2).previousAttemptId()).isEqualTo(runs.get(1).id());
        assertThat(runs.get(0).nextRetryAt()).isNotNull();
        assertThat(runs.get(1).nextRetryAt()).isNotNull();
        assertThat(runs.get(2).nextRetryAt()).isNull();
        assertThat(runs).allSatisfy(run -> assertThat(run.errorMessage()).startsWith("Simulated failure"));

        // No fourth attempt.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> executions(id).size() == 3);
        assertThat(retryTriggers(id)).isEmpty();
        JsonNode last = fetch(id).get("lastExecution");
        assertThat(last.get("status").asText()).isEqualTo("FAILED");
        assertThat(last.get("attempt").asInt()).isEqualTo(3);
        assertThat(last.get("maxAttempts").asInt()).isEqualTo(3);
    }

    @Test
    void aRetryThatSucceedsEndsTheChain() throws Exception {
        UUID id = createId(controllable("Flaky", FAIL_FIRST_ATTEMPT, "flaky", yearly(6)));

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED", "SUCCESS")));
        List<Execution> runs = executions(id);
        assertThat(runs.get(1).triggerType()).isEqualTo("RETRY");
        assertThat(runs.get(1).attempt()).isEqualTo(2);
        assertThat(runs.get(1).previousAttemptId()).isEqualTo(runs.get(0).id());
    }

    @Test
    void aPermanentFailureIsNotRetried() throws Exception {
        UUID id = createId(controllable("Hopeless", FAIL_PERMANENTLY, "permanent", yearly(7)));

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> executions(id).size() == 1);
        Execution run = executions(id).getFirst();
        assertThat(run.nextRetryAt()).isNull();
        assertThat(run.errorMessage()).isEqualTo("Simulated permanent failure");
    }

    @Test
    void storedParametersThatNoLongerMatchTheSchemaFailWithoutRetry() throws Exception {
        UUID id = createId(logScheduling("Corrupted", "fine", yearly(8)));
        jdbc.update("UPDATE schedulings SET parameters = '{}'::jsonb WHERE id = ?", id);

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> executions(id).size() == 1);
        assertThat(executions(id).getFirst().errorMessage()).contains("message");
    }

    @Test
    void aTaskThatNoLongerExistsFailsAndIsFlaggedAsUnknown() throws Exception {
        UUID id = createId(logScheduling("Orphaned task", "x", yearly(9)));
        jdbc.update("UPDATE schedulings SET task_key = 'REMOVED_TASK' WHERE id = ?", id);

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));
        assertThat(executions(id).getFirst().errorMessage()).isEqualTo("Unknown task: REMOVED_TASK");
        JsonNode task = fetch(id).get("task");
        assertThat(task.get("key").asText()).isEqualTo("REMOVED_TASK");
        assertThat(task.get("known").asBoolean()).isFalse();
    }

    @Test
    void anOverlappingRunOfTheSameSchedulingIsSkipped() throws Exception {
        UUID id = createId(controllable("Slow", BLOCK, "overlap", yearly(10)));
        runNow(id);
        controllableTask.awaitStarted(TIMEOUT);

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).contains("SKIPPED"));
        Execution skipped = executions(id).stream().filter(e -> e.status().equals("SKIPPED")).findFirst().orElseThrow();
        assertThat(skipped.errorMessage()).isEqualTo(ExecutionRunner.SKIPPED_MESSAGE);
        assertThat(skipped.triggerType()).isEqualTo("MANUAL");
        assertThat(skipped.finishedAt()).isNotNull();
        // The blocked run keeps beating while it waits.
        await().atMost(TIMEOUT).until(() -> executions(id).stream()
                .anyMatch(e -> e.status().equals("RUNNING") && e.lastHeartbeatAt().isAfter(e.startedAt())));

        controllableTask.releaseAll();

        await().atMost(TIMEOUT).until(() -> statuses(id).containsAll(List.of("SUCCESS", "SKIPPED"))
                && !statuses(id).contains("RUNNING"));
    }

    @Test
    void differentSchedulingsWithTheSameTaskAndParametersRunAtTheSameTime() throws Exception {
        UUID first = createId(controllable("First", BLOCK, "shared", yearly(11)));
        UUID second = createId(controllable("Second", BLOCK, "shared", yearly(12)));

        runNow(first);
        runNow(second);
        controllableTask.awaitStarted(TIMEOUT);
        controllableTask.awaitStarted(TIMEOUT);

        assertThat(statuses(first)).containsExactly("RUNNING");
        assertThat(statuses(second)).containsExactly("RUNNING");
        controllableTask.releaseAll();
        await().atMost(TIMEOUT).until(() -> statuses(first).equals(List.of("SUCCESS"))
                && statuses(second).equals(List.of("SUCCESS")));
    }

    @Test
    void pausingCancelsAPendingRetry() throws Exception {
        retryPolicy.setDelay(Duration.ofSeconds(2));
        UUID id = createId(controllable("Fails, then paused", FAIL, "pause-cancels", yearly(13)));
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));
        assertThat(retryTriggers(id)).hasSize(1);

        pause(id);

        assertThat(executions(id).getFirst().retryCancelledReason()).isEqualTo("PAUSED");
        assertThat(retryTriggers(id)).isEmpty();
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> executions(id).size() == 1);
    }

    @Test
    void aNewRunSupersedesAPendingRetry() throws Exception {
        retryPolicy.setDelay(Duration.ofSeconds(2));
        UUID id = createId(controllable("Fails once", FAIL_FIRST_ATTEMPT, "supersede", yearly(14)));
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));

        runNow(id);

        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED", "SUCCESS")));
        List<Execution> runs = executions(id);
        assertThat(runs.get(0).retryCancelledReason()).isEqualTo("SUPERSEDED");
        assertThat(runs.get(1).triggerType()).isEqualTo("MANUAL");
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> executions(id).size() == 2);
        assertThat(retryTriggers(id)).isEmpty();
    }

    @Test
    void editingCancelsAPendingRetry() throws Exception {
        retryPolicy.setDelay(Duration.ofSeconds(2));
        UUID id = createId(controllable("Fails, then edited", FAIL, "edit-cancels", yearly(15)));
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("FAILED")));
        String token = acquireLock(id, "tab-A");
        Map<String, Object> edit = controllable("Fails, then edited", FAIL, "edited", yearly(15));
        edit.put("version", 0);

        send(put("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, token), edit)
                .andExpect(status().isOk());

        assertThat(executions(id).getFirst().retryCancelledReason()).isEqualTo("EDITED");
        assertThat(retryTriggers(id)).isEmpty();
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> executions(id).size() == 1);
    }

    @Test
    void aRunDeclaredStuckIsStoppedByItsNextHeartbeatAndKeepsItsStatus() throws Exception {
        UUID id = createId(controllable("Hangs", BLOCK, "stuck", yearly(16)));
        runNow(id);
        controllableTask.awaitStarted(TIMEOUT);

        jdbc.update("""
                UPDATE task_executions SET status = 'STUCK', finished_at = now(), error_message = 'declared stuck'
                 WHERE scheduling_id = ? AND status = 'RUNNING'
                """, id);

        await().atMost(TIMEOUT).until(() -> controllableTask.stoppedByHeartbeat() == 1);
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> statuses(id).equals(List.of("STUCK")));
        assertThat(executions(id).getFirst().errorMessage()).isEqualTo("declared stuck");
        assertThat(retryTriggers(id)).isEmpty();
    }
}
