package com.memcyco.scheduler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * All writes to {@code task_executions}, as single SQL statements that use the <b>database clock</b>
 * ({@code now()}), so several backend instances agree on time.
 * <p>
 * Status changes are conditional ({@code WHERE status = 'RUNNING'}): a run that was already marked STUCK, or whose
 * scheduling was deleted, is never overwritten by a late result.
 */
@Repository
public class TaskExecutionWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TaskExecutionWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts the attempt as RUNNING. This is the runtime lock: the partial unique index
     * {@code (scheduling_id, run_key) WHERE status = 'RUNNING'} lets at most one such row exist.
     *
     * @return false if the same scheduling is already running the same task + parameters
     */
    public boolean startRunning(NewExecution e) {
        return jdbc.update("""
                INSERT INTO task_executions (id, scheduling_id, task_key, parameters, run_key, status, trigger_type,
                                             attempt, previous_attempt_id, executed_by, started_at, last_heartbeat_at)
                VALUES (:id, :schedulingId, :taskKey, CAST(:parameters AS jsonb), :runKey, 'RUNNING', :triggerType,
                        :attempt, :previousAttemptId, :executedBy, now(), now())
                ON CONFLICT (scheduling_id, run_key) WHERE status = 'RUNNING' DO NOTHING
                """, params(e)) == 1;
    }

    /** Records an attempt that ended without running (SKIPPED, or FAILED before it could start). */
    public void recordFinished(NewExecution e, ExecutionStatus status, String message) {
        jdbc.update("""
                INSERT INTO task_executions (id, scheduling_id, task_key, parameters, run_key, status, trigger_type,
                                             attempt, previous_attempt_id, executed_by, started_at, last_heartbeat_at,
                                             finished_at, error_message)
                VALUES (:id, :schedulingId, :taskKey, CAST(:parameters AS jsonb), :runKey, :status, :triggerType,
                        :attempt, :previousAttemptId, :executedBy, now(), now(), now(), :message)
                """, params(e).addValue("status", status.name()).addValue("message", message));
    }

    /** @return false if the run is no longer RUNNING (marked STUCK, or its scheduling was deleted) */
    public boolean markSucceeded(UUID executionId) {
        return jdbc.update("""
                UPDATE task_executions SET status = 'SUCCESS', finished_at = now()
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", executionId)) == 1;
    }

    /**
     * @param nextRetryAt when the next attempt is scheduled, or {@code null} if there will be none
     * @return false if the run is no longer RUNNING
     */
    public boolean markFailed(UUID executionId, String errorMessage, Instant nextRetryAt) {
        return jdbc.update("""
                UPDATE task_executions
                   SET status = 'FAILED', finished_at = now(), error_message = :message, next_retry_at = :nextRetryAt
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", executionId)
                .addValue("message", errorMessage)
                .addValue("nextRetryAt", toOffset(nextRetryAt), Types.TIMESTAMP_WITH_TIMEZONE)) == 1;
    }

    /** @return false if the run is no longer RUNNING, so the task must stop */
    public boolean heartbeat(UUID executionId) {
        return jdbc.update("""
                UPDATE task_executions SET last_heartbeat_at = now()
                 WHERE id = :id AND status = 'RUNNING'
                """, new MapSqlParameterSource("id", executionId)) == 1;
    }

    /**
     * Marks failed attempts whose retry has not happened yet (no successor attempt exists) as cancelled.
     *
     * @return number of attempts whose pending retry was cancelled
     */
    public int cancelPendingRetries(UUID schedulingId, RetryCancelledReason reason) {
        return jdbc.update("""
                UPDATE task_executions e SET retry_cancelled_reason = :reason
                 WHERE e.scheduling_id = :schedulingId
                   AND e.next_retry_at IS NOT NULL
                   AND e.retry_cancelled_reason IS NULL
                   AND NOT EXISTS (SELECT 1 FROM task_executions n WHERE n.previous_attempt_id = e.id)
                """, new MapSqlParameterSource("schedulingId", schedulingId).addValue("reason", reason.name()));
    }

    /** RUNNING attempts without a heartbeat for longer than {@code timeout} become STUCK (releasing the lock). */
    public int markStuck(Duration timeout, String message) {
        return jdbc.update("""
                UPDATE task_executions SET status = 'STUCK', finished_at = now(), error_message = :message
                 WHERE status = 'RUNNING' AND last_heartbeat_at < now() - :seconds * interval '1 second'
                """, new MapSqlParameterSource("seconds", timeout.toSeconds()).addValue("message", message));
    }

    /** Deletes up to {@code batchSize} finished attempts started more than {@code age} ago. */
    public int deleteFinishedOlderThan(Duration age, int batchSize) {
        return jdbc.update("""
                DELETE FROM task_executions WHERE id IN (
                    SELECT id FROM task_executions
                     WHERE started_at < now() - :seconds * interval '1 second' AND status <> 'RUNNING'
                     LIMIT :batchSize)
                """, new MapSqlParameterSource("seconds", age.toSeconds()).addValue("batchSize", batchSize));
    }

    private MapSqlParameterSource params(NewExecution e) {
        return new MapSqlParameterSource()
                .addValue("id", e.id())
                .addValue("schedulingId", e.schedulingId())
                .addValue("taskKey", e.taskKey())
                .addValue("parameters", toJson(e))
                .addValue("runKey", e.runKey())
                .addValue("triggerType", e.triggerType().name())
                .addValue("attempt", e.attempt())
                .addValue("previousAttemptId", e.previousAttemptId(), Types.OTHER)
                .addValue("executedBy", e.executedBy());
    }

    private String toJson(NewExecution e) {
        try {
            return objectMapper.writeValueAsString(e.parameters());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Parameters are not serializable", ex);
        }
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
