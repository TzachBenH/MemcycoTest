package com.memcyco.scheduler.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memcyco.scheduler.controller.ApiHeaders;
import com.memcyco.scheduler.service.QuartzSchedulingGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of all integration tests: the whole application (API, JPA, Flyway, clustered Quartz with the JDBC
 * store) against a real PostgreSQL 15 (see {@link TestDatabase}). All subclasses share one application context;
 * every test starts with no schedulings, no Quartz jobs of schedulings and no history.
 */
@SpringBootTest(properties = {
        "app.instance-id=it-node",
        // Every beat is written, so a run marked STUCK is stopped at its next beat.
        "app.execution.heartbeat-throttle=0s",
        // The tests call the monitor directly instead of waiting for its trigger.
        "app.execution.stuck-check-interval=1h",
        "spring.flyway.clean-disabled=false"
})
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
public abstract class IntegrationTest {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);
    protected static final String EDITOR_ID = ApiHeaders.EDITOR_ID;
    protected static final String EDIT_LOCK_TOKEN = ApiHeaders.EDIT_LOCK_TOKEN;
    protected static final String INSTANCE_ID = "it-node";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected Scheduler scheduler;
    @Autowired
    protected TransactionTemplate transactions;
    @Autowired
    protected ControllableTask controllableTask;
    @Autowired
    protected FastRetryPolicy retryPolicy;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @BeforeEach
    void startFromAnEmptySystem() {
        controllableTask.reset();
        retryPolicy.reset();
        deleteAllSchedulings();
    }

    @AfterEach
    void releaseBlockedRuns() {
        controllableTask.releaseAll();
    }

    /** Removes schedulings' Quartz jobs (with all their triggers), waits for running jobs, then empties the tables. */
    private void deleteAllSchedulings() {
        inTransaction(() -> scheduler.deleteJobs(new ArrayList<>(
                scheduler.getJobKeys(GroupMatcher.jobGroupEquals(QuartzSchedulingGateway.JOB_GROUP)))));
        await().atMost(TIMEOUT).until(() -> scheduler.getCurrentlyExecutingJobs().stream()
                .map(JobExecutionContext::getJobDetail)
                .noneMatch(job -> QuartzSchedulingGateway.JOB_GROUP.equals(job.getKey().getGroup())));
        // Executions and edit locks are deleted with their scheduling (ON DELETE CASCADE).
        jdbc.update("DELETE FROM schedulings");
        jdbc.update("DELETE FROM schedule_timings");
    }

    // ---- API helpers ----

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return body(result.andReturn());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Creates a scheduling (expects 201) and returns the response body. */
    protected JsonNode create(Object request) throws Exception {
        return body(send(post("/api/schedulings"), request).andExpect(status().isCreated()));
    }

    protected UUID createId(Object request) throws Exception {
        return UUID.fromString(create(request).get("id").asText());
    }

    /** GET /api/schedulings/{id}, expecting 200. */
    protected JsonNode fetch(UUID schedulingId) throws Exception {
        return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/schedulings/{id}", schedulingId)).andExpect(status().isOk()));
    }

    protected void pause(UUID schedulingId) throws Exception {
        mvc.perform(post("/api/schedulings/{id}/pause", schedulingId)).andExpect(status().isOk());
    }

    protected void resume(UUID schedulingId) throws Exception {
        mvc.perform(post("/api/schedulings/{id}/resume", schedulingId)).andExpect(status().isOk());
    }

    protected void runNow(UUID schedulingId) throws Exception {
        mvc.perform(post("/api/schedulings/{id}/run", schedulingId)).andExpect(status().isAccepted());
    }

    /** Acquires the edit lock for the given browser tab and returns the lock token. */
    protected String acquireLock(UUID schedulingId, String editorId) throws Exception {
        return body(mvc.perform(post("/api/schedulings/{id}/edit-lock", schedulingId).header(EDITOR_ID, editorId))
                .andExpect(status().isOk())).get("lockToken").asText();
    }

    // ---- database / Quartz helpers ----

    /** One row of task_executions. */
    public record Execution(UUID id, String status, String triggerType, int attempt, UUID previousAttemptId,
                            Instant nextRetryAt, String retryCancelledReason, String executedBy,
                            String errorMessage, Instant startedAt, Instant lastHeartbeatAt, Instant finishedAt) {
    }

    /** The scheduling's execution attempts, oldest first. */
    protected List<Execution> executions(UUID schedulingId) {
        return jdbc.query("""
                SELECT * FROM task_executions WHERE scheduling_id = ? ORDER BY started_at, attempt, id
                """, (rs, i) -> new Execution(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("trigger_type"), rs.getInt("attempt"), rs.getObject("previous_attempt_id", UUID.class),
                instant(rs, "next_retry_at"), rs.getString("retry_cancelled_reason"), rs.getString("executed_by"),
                rs.getString("error_message"), instant(rs, "started_at"), instant(rs, "last_heartbeat_at"),
                instant(rs, "finished_at")), schedulingId);
    }

    protected List<String> statuses(UUID schedulingId) {
        return executions(schedulingId).stream().map(Execution::status).toList();
    }

    protected boolean hasJob(UUID schedulingId) throws SchedulerException {
        return scheduler.checkExists(JobKey.jobKey(schedulingId.toString(), QuartzSchedulingGateway.JOB_GROUP));
    }

    protected boolean hasMainTrigger(UUID schedulingId) throws SchedulerException {
        return scheduler.checkExists(TriggerKey.triggerKey(schedulingId.toString(), "main"));
    }

    /** Pending one-shot retry triggers of the scheduling. */
    protected List<Trigger> retryTriggers(UUID schedulingId) throws SchedulerException {
        return scheduler.getTriggersOfJob(JobKey.jobKey(schedulingId.toString(), QuartzSchedulingGateway.JOB_GROUP))
                .stream()
                .filter(trigger -> "retry".equals(trigger.getKey().getGroup()))
                .map(Trigger.class::cast)
                .toList();
    }

    @FunctionalInterface
    protected interface QuartzWork {
        void run() throws SchedulerException;
    }

    /** Quartz writes use the database and must run in a Spring transaction (see QuartzSchedulingGateway). */
    protected void inTransaction(QuartzWork work) {
        transactions.executeWithoutResult(status -> {
            try {
                work.run();
            } catch (SchedulerException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
