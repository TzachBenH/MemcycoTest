package com.memcyco.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One execution attempt of a scheduling (history row).
 * <p>
 * The runtime lock is the partial unique index {@code (scheduling_id, run_key) WHERE status = 'RUNNING'}:
 * inserting a second RUNNING row for the same scheduling + task/parameters fails, and the attempt is recorded as
 * {@link ExecutionStatus#SKIPPED} instead. All writes are single SQL statements in
 * {@link com.memcyco.scheduler.repository.TaskExecutionWriter} (using the database clock and conditional updates),
 * so this entity is read-only.
 */
@Entity
@Table(name = "task_executions")
public class TaskExecution {

    @Id
    private UUID id;

    @Column(name = "scheduling_id", nullable = false)
    private UUID schedulingId;

    @Column(name = "task_key", nullable = false, length = 50)
    private String taskKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "run_key", nullable = false, length = 64)
    private String runKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "previous_attempt_id")
    private UUID previousAttemptId;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_cancelled_reason", length = 20)
    private RetryCancelledReason retryCancelledReason;

    @Column(name = "executed_by", nullable = false, length = 100)
    private String executedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected TaskExecution() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchedulingId() {
        return schedulingId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getRunKey() {
        return runKey;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public int getAttempt() {
        return attempt;
    }

    public UUID getPreviousAttemptId() {
        return previousAttemptId;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public RetryCancelledReason getRetryCancelledReason() {
        return retryCancelledReason;
    }

    public String getExecutedBy() {
        return executedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
