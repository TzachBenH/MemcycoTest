package com.memcyco.scheduler.task.impl;

import com.memcyco.scheduler.support.IntegrationTest;
import com.memcyco.scheduler.task.InvalidTaskParametersException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.UUID;

import static com.memcyco.scheduler.support.Requests.logScheduling;
import static com.memcyco.scheduler.support.Requests.scheduling;
import static com.memcyco.scheduler.support.Requests.yearly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The predefined read-only reports, against the real schema. */
@ExtendWith(OutputCaptureExtension.class)
class DbQueryTaskIT extends IntegrationTest {

    @Autowired
    private DbQueryTask task;

    @Test
    void reportsSchedulingsByStatus(CapturedOutput output) throws Exception {
        createId(logScheduling("Active", "a", yearly(3)));
        pause(createId(logScheduling("Paused", "b", yearly(4))));

        task.execute(Map.of("report", "SCHEDULINGS_BY_STATUS"), () -> { });

        assertThat(output).contains("[DB_QUERY task] Schedulings by status: ACTIVE=1, PAUSED=1");
    }

    @Test
    void reportsSchedulingsByTask(CapturedOutput output) throws Exception {
        createId(logScheduling("Log 1", "a", yearly(3)));
        createId(logScheduling("Log 2", "b", yearly(4)));
        createId(scheduling("Report", "DB_QUERY", Map.of("report", "SCHEDULINGS_BY_TASK"), yearly(5)));

        task.execute(Map.of("report", "SCHEDULINGS_BY_TASK"), () -> { });

        assertThat(output).contains("[DB_QUERY task] Schedulings by task: DB_QUERY=1, LOG=2");
    }

    @Test
    void reportsExecutionsOfTheRequestedTimeWindow(CapturedOutput output) throws Exception {
        UUID id = createId(logScheduling("Busy", "a", yearly(3)));
        insertExecution(id, "SUCCESS", 1);
        insertExecution(id, "FAILED", 2);
        insertExecution(id, "SUCCESS", 30);

        task.execute(Map.of("report", "EXECUTIONS_BY_STATUS", "lastHours", 24), () -> { });
        assertThat(output).contains("[DB_QUERY task] Executions by status (last 24h): FAILED=1, SUCCESS=1");

        task.execute(Map.of("report", "EXECUTIONS_BY_STATUS", "lastHours", 48), () -> { });
        assertThat(output).contains("[DB_QUERY task] Executions by status (last 48h): FAILED=1, SUCCESS=2");
    }

    @Test
    void saysSoWhenThereIsNothingToReport(CapturedOutput output) throws Exception {
        task.execute(Map.of("report", "EXECUTIONS_BY_STATUS"), () -> { });

        assertThat(output).contains("[DB_QUERY task] Executions by status (last 24h): no rows");
    }

    @Test
    void onlyPredefinedReportsAndBoundedWindowsAreAccepted() {
        assertThatThrownBy(() -> task.execute(Map.of("report", "DROP TABLE schedulings"), () -> { }))
                .isInstanceOf(InvalidTaskParametersException.class);
        assertThatThrownBy(() -> task.execute(Map.of("report", "EXECUTIONS_BY_STATUS", "lastHours", 721), () -> { }))
                .isInstanceOf(InvalidTaskParametersException.class);
    }

    private void insertExecution(UUID schedulingId, String status, int hoursAgo) {
        jdbc.update("""
                INSERT INTO task_executions (id, scheduling_id, task_key, parameters, run_key, status, trigger_type,
                                             attempt, executed_by, started_at, last_heartbeat_at, finished_at)
                VALUES (?, ?, 'LOG', '{}'::jsonb, ?, ?, 'SCHEDULED', 1, 'it-node',
                        now() - ? * interval '1 hour', now() - ? * interval '1 hour', now() - ? * interval '1 hour')
                """, UUID.randomUUID(), schedulingId, "key-" + UUID.randomUUID(), status, hoursAgo, hoursAgo,
                hoursAgo);
    }
}
