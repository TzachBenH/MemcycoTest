package com.memcyco.scheduler.service;

import com.memcyco.scheduler.config.AppProperties;
import com.memcyco.scheduler.config.ExecutionProperties;
import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.TriggerType;
import com.memcyco.scheduler.repository.NewExecution;
import com.memcyco.scheduler.repository.SchedulingRepository;
import com.memcyco.scheduler.repository.TaskExecutionWriter;
import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs one attempt of a scheduling when its trigger fires:
 * <pre>
 * load scheduling ─► (scheduled/manual run) cancel a pending retry: SUPERSEDED
 *                 ─► unknown task?                         → FAILED, no retry
 *                 ─► insert RUNNING (runtime lock)  taken? → SKIPPED
 *                 ─► task.execute(parameters, heartbeat)      (no transaction held while the task runs)
 *                 ─► SUCCESS, or FAILED + retry trigger in 10s / 20s while attempts remain
 * </pre>
 * Each database step is its own short transaction; a result is only recorded while the row is still RUNNING.
 */
@Service
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);
    static final String SKIPPED_MESSAGE =
            "Skipped: the previous run of this scheduling with the same task and parameters is still running";
    private static final int MAX_ERROR_LENGTH = 2000;

    private final SchedulingRepository schedulings;
    private final TaskRegistry taskRegistry;
    private final FingerprintService fingerprints;
    private final TaskExecutionWriter executions;
    private final RetryService retries;
    private final RetryPolicy retryPolicy;
    private final TransactionTemplate transactions;
    private final AppProperties appProperties;
    private final ExecutionProperties executionProperties;
    private final Clock clock;

    @SuppressWarnings("java:S107") // the orchestrator of a run simply has many collaborators
    public ExecutionRunner(SchedulingRepository schedulings, TaskRegistry taskRegistry,
                           FingerprintService fingerprints, TaskExecutionWriter executions, RetryService retries,
                           RetryPolicy retryPolicy, TransactionTemplate transactions, AppProperties appProperties,
                           ExecutionProperties executionProperties, Clock clock) {
        this.schedulings = schedulings;
        this.taskRegistry = taskRegistry;
        this.fingerprints = fingerprints;
        this.executions = executions;
        this.retries = retries;
        this.retryPolicy = retryPolicy;
        this.transactions = transactions;
        this.appProperties = appProperties;
        this.executionProperties = executionProperties;
        this.clock = clock;
    }

    public void run(ExecutionRequest request) {
        try {
            Optional<StartedRun> started = transactions.execute(status -> start(request));
            if (started != null) {
                started.ifPresent(this::executeAndRecord);
            }
        } catch (RuntimeException e) {
            log.error("Execution engine error for scheduling {}", request.schedulingId(), e);
        }
    }

    private Optional<StartedRun> start(ExecutionRequest request) {
        Optional<Scheduling> found = schedulings.findById(request.schedulingId());
        if (found.isEmpty()) {
            log.info("Scheduling {} no longer exists; nothing to run", request.schedulingId());
            return Optional.empty();
        }
        Scheduling scheduling = found.get();
        if (request.triggerType() != TriggerType.RETRY) {
            retries.cancelPending(scheduling.getId(), RetryCancelledReason.SUPERSEDED);
        }

        NewExecution execution = new NewExecution(UUID.randomUUID(), scheduling.getId(), scheduling.getTaskKey(),
                scheduling.getParameters(), fingerprints.runKey(scheduling.getTaskKey(), scheduling.getParameters()),
                request.triggerType(), request.attempt(), request.previousAttemptId(), appProperties.instanceId());
        String label = label(scheduling.getName(), execution);

        Optional<Task> task = taskRegistry.find(scheduling.getTaskKey());
        if (task.isEmpty()) {
            executions.recordFinished(execution, ExecutionStatus.FAILED, "Unknown task: " + scheduling.getTaskKey());
            log.warn("{} failed: unknown task {}", label, scheduling.getTaskKey());
            return Optional.empty();
        }
        if (!executions.startRunning(execution)) {
            executions.recordFinished(execution, ExecutionStatus.SKIPPED, SKIPPED_MESSAGE);
            log.info("{} skipped: the previous run is still in progress", label);
            return Optional.empty();
        }
        log.info("{} started", label);
        return Optional.of(new StartedRun(execution, task.get(), label));
    }

    private void executeAndRecord(StartedRun run) {
        Instant start = clock.instant();
        DatabaseHeartbeat heartbeat = new DatabaseHeartbeat(run.execution().id(), executions, clock,
                executionProperties.heartbeatThrottle());
        Throwable failure = null;
        try {
            run.task().execute(run.execution().parameters(), heartbeat);
        } catch (ExecutionNoLongerRunningException e) {
            log.warn("{} stopped: {}", run.label(), e.getMessage());
            return;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) { // NOSONAR: any task failure is recorded (and retried) instead of escaping to Quartz
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failure = e;
        }
        long millis = Duration.between(start, clock.instant()).toMillis();
        Throwable result = failure;
        transactions.executeWithoutResult(status -> record(run, result, millis));
    }

    private void record(StartedRun run, Throwable failure, long millis) {
        NewExecution execution = run.execution();
        if (failure == null) {
            if (executions.markSucceeded(execution.id())) {
                log.info("{} succeeded in {} ms", run.label(), millis);
            } else {
                log.warn("{} finished after it was marked STUCK or deleted; result not recorded", run.label());
            }
            return;
        }

        boolean retry = retryPolicy.shouldRetry(failure, execution.attempt());
        Instant retryAt = retry ? clock.instant().plus(retryPolicy.delayBeforeAttempt(execution.attempt() + 1)) : null;
        if (!executions.markFailed(execution.id(), errorMessage(failure), retryAt)) {
            log.warn("{} failed after it was marked STUCK or deleted; result not recorded", run.label());
            return;
        }
        if (retry) {
            retries.scheduleRetry(execution.schedulingId(), execution.attempt() + 1, execution.id(), retryAt);
            log.warn("{} failed: {} — retry at {}", run.label(), errorMessage(failure), retryAt);
        } else {
            log.warn("{} failed: {} — no more retries", run.label(), errorMessage(failure));
        }
    }

    private static String label(String schedulingName, NewExecution e) {
        return "Run of \"%s\" [%s %s, attempt %d/%d]".formatted(schedulingName, e.taskKey(), e.triggerType(),
                e.attempt(), RetryPolicy.MAX_ATTEMPTS);
    }

    static String errorMessage(Throwable failure) {
        String message = failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH) + "…";
    }

    private record StartedRun(NewExecution execution, Task task, String label) {
    }
}
