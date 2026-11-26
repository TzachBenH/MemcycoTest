package com.memcyco.scheduler.service;

import com.memcyco.scheduler.config.AppProperties;
import com.memcyco.scheduler.config.ExecutionProperties;
import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.TriggerType;
import com.memcyco.scheduler.entity.timing.CronTiming;
import com.memcyco.scheduler.repository.NewExecution;
import com.memcyco.scheduler.repository.SchedulingRepository;
import com.memcyco.scheduler.repository.TaskExecutionWriter;
import com.memcyco.scheduler.scheduling.TimingStrategies;
import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.Heartbeat;
import com.memcyco.scheduler.task.NonRetryableTaskException;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskParameters;
import com.memcyco.scheduler.task.TaskRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The run flow: lock/skip, success, failure → retry, last attempt, permanent failures, supersede, stuck. */
class ExecutionRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

    private final SchedulingRepository schedulings = mock(SchedulingRepository.class);
    private final TaskExecutionWriter writer = mock(TaskExecutionWriter.class);
    private final RetryService retries = mock(RetryService.class);
    private final AtomicInteger runs = new AtomicInteger();
    private RuntimeException failWith;
    private final Task task = new Task() {
        @Override
        public String key() {
            return "TEST";
        }

        @Override
        public String name() {
            return "Test";
        }

        @Override
        public String description() {
            return "";
        }

        @Override
        public List<ParameterDefinition> parameters() {
            return List.of();
        }

        @Override
        protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) {
            runs.incrementAndGet();
            heartbeat.beat();
            if (failWith != null) {
                throw failWith;
            }
        }
    };
    private final Scheduling scheduling = new Scheduling("Nightly", "TEST", Map.of(), true,
            new CronTiming("0 0 3 * * ?", "UTC"), "fp");
    private ExecutionRunner runner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        runner = new ExecutionRunner(schedulings, new TaskRegistry(List.of(task)),
                new FingerprintService(new TimingStrategies(List.of())), writer, retries, new RetryPolicy(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AppProperties("node-1", new AppProperties.Cors(List.of())),
                new ExecutionProperties(Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(10),
                        Duration.ofDays(30), "0 0 3 * * ?"),
                clock);
        when(schedulings.findById(scheduling.getId())).thenReturn(Optional.of(scheduling));
        when(writer.startRunning(any())).thenReturn(true);
        when(writer.markSucceeded(any())).thenReturn(true);
        when(writer.markFailed(any(), any(), any())).thenReturn(true);
    }

    private void run(TriggerType type, int attempt) {
        runner.run(new ExecutionRequest(scheduling.getId(), type, attempt, attempt > 1 ? UUID.randomUUID() : null));
    }

    private NewExecution startedExecution() {
        ArgumentCaptor<NewExecution> captor = ArgumentCaptor.forClass(NewExecution.class);
        verify(writer).startRunning(captor.capture());
        return captor.getValue();
    }

    @Test
    void successfulRunIsRecordedAndSupersedesAPendingRetry() {
        run(TriggerType.SCHEDULED, 1);

        NewExecution execution = startedExecution();
        assertThat(execution.executedBy()).isEqualTo("node-1");
        assertThat(execution.attempt()).isEqualTo(1);
        assertThat(runs).hasValue(1);
        verify(writer).markSucceeded(execution.id());
        verify(retries).cancelPending(scheduling.getId(), RetryCancelledReason.SUPERSEDED);
    }

    @Test
    void failedFirstAttemptSchedulesARetryInAboutTenSeconds() {
        failWith = new IllegalStateException("SMTP timeout");

        run(TriggerType.SCHEDULED, 1);

        NewExecution execution = startedExecution();
        ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(writer).markFailed(eq(execution.id()), eq("SMTP timeout"), retryAt.capture());
        assertThat(retryAt.getValue()).isBetween(NOW.plusSeconds(8), NOW.plusSeconds(12));
        verify(retries).scheduleRetry(scheduling.getId(), 2, execution.id(), retryAt.getValue());
    }

    @Test
    void thirdAttemptFailureIsFinal() {
        failWith = new IllegalStateException("still down");

        run(TriggerType.RETRY, 3);

        verify(writer).markFailed(any(), eq("still down"), isNull());
        verify(retries, never()).scheduleRetry(any(), anyInt(), any(), any());
    }

    @Test
    void permanentFailureIsNotRetried() {
        failWith = new NonRetryableTaskException("invalid address");

        run(TriggerType.SCHEDULED, 1);

        verify(writer).markFailed(any(), eq("invalid address"), isNull());
        verify(retries, never()).scheduleRetry(any(), anyInt(), any(), any());
    }

    @Test
    void retryAttemptDoesNotCancelItself() {
        run(TriggerType.RETRY, 2);

        verify(retries, never()).cancelPending(any(), any());
        verify(writer).markSucceeded(notNull());
    }

    @Test
    void runWhileThePreviousIsStillRunningIsSkipped() {
        when(writer.startRunning(any())).thenReturn(false);

        run(TriggerType.MANUAL, 1);

        assertThat(runs).hasValue(0);
        verify(writer).recordFinished(any(), eq(ExecutionStatus.SKIPPED), eq(ExecutionRunner.SKIPPED_MESSAGE));
    }

    @Test
    void unknownTaskFailsWithoutRunningOrRetrying() {
        Scheduling orphan = new Scheduling("Old", "REMOVED_TASK", Map.of(), true, new CronTiming("0 0 3 * * ?", "UTC"), "x");
        when(schedulings.findById(orphan.getId())).thenReturn(Optional.of(orphan));

        runner.run(new ExecutionRequest(orphan.getId(), TriggerType.SCHEDULED, 1, null));

        verify(writer).recordFinished(any(), eq(ExecutionStatus.FAILED), eq("Unknown task: REMOVED_TASK"));
        verify(writer, never()).startRunning(any());
        verify(retries, never()).scheduleRetry(any(), anyInt(), any(), any());
    }

    @Test
    void deletedSchedulingRunsNothing() {
        runner.run(new ExecutionRequest(UUID.randomUUID(), TriggerType.SCHEDULED, 1, null));

        verify(writer, never()).startRunning(any());
        assertThat(runs).hasValue(0);
    }

    @Test
    void runDeclaredStuckIsNotOverwritten() {
        failWith = new ExecutionNoLongerRunningException("marked stuck");

        run(TriggerType.SCHEDULED, 1);

        verify(writer, never()).markSucceeded(any());
        verify(writer, never()).markFailed(any(), any(), any());
    }

    @Test
    void lateFailureAfterStuckDoesNotScheduleARetry() {
        failWith = new IllegalStateException("boom");
        when(writer.markFailed(any(), any(), any())).thenReturn(false);

        run(TriggerType.SCHEDULED, 1);

        verify(retries, never()).scheduleRetry(any(), anyInt(), any(), any());
    }
}
