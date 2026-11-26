package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.memcyco.scheduler.support.Requests.cron;
import static com.memcyco.scheduler.support.Requests.logScheduling;
import static com.memcyco.scheduler.support.Requests.scheduling;
import static com.memcyco.scheduler.support.Requests.weekly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD, paging/sorting/filtering, pause/resume and the error format of the schedulings API. */
class SchedulingApiIT extends IntegrationTest {

    @Test
    void createsASchedulingWithNormalizedParametersAndSchedulesIt() throws Exception {
        UUID id = UUID.fromString(body(send(post("/api/schedulings"),
                logScheduling("Morning log", "  Good morning  ", cron("0 0 9 ? * MON-FRI")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Morning log"))
                .andExpect(jsonPath("$.task.key").value("LOG"))
                .andExpect(jsonPath("$.task.name").value("Log Message"))
                .andExpect(jsonPath("$.task.known").value(true))
                .andExpect(jsonPath("$.parameters.message").value("Good morning"))
                .andExpect(jsonPath("$.parameters.level").value("INFO"))
                .andExpect(jsonPath("$.timing.type").value("CRON"))
                .andExpect(jsonPath("$.timing.expression").value("0 0 9 ? * MON-FRI"))
                .andExpect(jsonPath("$.timing.timezone").value("UTC"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextFireTime").isNotEmpty())
                .andExpect(jsonPath("$.lastExecution").value(nullValue()))
                .andExpect(jsonPath("$.editLock").value(nullValue()))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").value("anonymous"))
                .andExpect(jsonPath("$.updatedBy").value("anonymous")))
                .get("id").asText());

        mvc.perform(get("/api/schedulings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        assertThat(hasJob(id)).isTrue();
        assertThat(hasMainTrigger(id)).isTrue();
    }

    @Test
    void returnsTheLocationOfTheNewScheduling() throws Exception {
        var result = send(post("/api/schedulings"), logScheduling("Located", "here", cron("0 0 9 * * ?")))
                .andExpect(status().isCreated())
                .andReturn();
        String id = body(result).get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/schedulings/" + id);
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() throws Exception {
        Map<String, Object> request = scheduling("  ", "LOG", Map.of("level", "LOUD"),
                cron("not a cron", "Mars/Olympus"));

        send(post("/api/schedulings"), request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").value("is required"))
                .andExpect(jsonPath("$.errors['parameters.message']").exists())
                .andExpect(jsonPath("$.errors['parameters.level']").exists())
                .andExpect(jsonPath("$.errors['timing.timezone']").value("is not a valid timezone"))
                .andExpect(jsonPath("$.errors['timing.expression']").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedulings", Integer.class)).isZero();
    }

    @Test
    void rejectsAnUnknownTaskAndAOneTimeRunInThePast() throws Exception {
        Map<String, Object> request = scheduling("Old", "NO_SUCH_TASK", Map.of(),
                Map.of("type", "ONE_TIME", "runAt", "2020-01-01T00:00:00Z", "timezone", "UTC"));

        send(post("/api/schedulings"), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.taskKey").value("is not a known task"))
                .andExpect(jsonPath("$.errors['timing.runAt']").value("must be in the future"));
    }

    @Test
    void rejectsUnknownTimingTypesWrongValueTypesAndMalformedJson() throws Exception {
        mvc.perform(post("/api/schedulings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "x", "taskKey": "LOG", "parameters": {"message": "m"},
                         "timing": {"type": "DAILY", "timezone": "UTC"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['timing.type']").value("must be one of ONE_TIME, INTERVAL, WEEKLY, CRON"));

        mvc.perform(post("/api/schedulings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "x", "taskKey": "LOG", "parameters": {"message": "m"},
                         "timing": {"type": "INTERVAL", "every": "often", "unit": "MINUTES", "timezone": "UTC"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['timing.every']").value("has an invalid value"));

        mvc.perform(post("/api/schedulings").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("is not valid JSON"));
    }

    @Test
    void listsWithPagingSortingSearchAndFilters() throws Exception {
        createId(logScheduling("Alpha log", "a", cron("0 0 1 * * ?")));
        createId(scheduling("Beta report", "DB_QUERY", Map.of("report", "SCHEDULINGS_BY_TASK"), cron("0 0 2 * * ?")));
        UUID gamma = createId(logScheduling("gamma LOG", "g", cron("0 0 3 * * ?")));
        pause(gamma);

        mvc.perform(get("/api/schedulings").param("size", "2").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Alpha log", "Beta report")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/schedulings").param("size", "2").param("page", "1").param("sort", "name,asc"))
                .andExpect(jsonPath("$.content[*].name").value(contains("gamma LOG")));
        mvc.perform(get("/api/schedulings").param("sort", "name,desc"))
                .andExpect(jsonPath("$.content[*].name").value(contains("gamma LOG", "Beta report", "Alpha log")));

        mvc.perform(get("/api/schedulings").param("search", "LOG"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Alpha log", "gamma LOG")));
        mvc.perform(get("/api/schedulings").param("taskKey", "DB_QUERY"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Beta report")));
        mvc.perform(get("/api/schedulings").param("status", "PAUSED"))
                .andExpect(jsonPath("$.content[*].name").value(contains("gamma LOG")))
                .andExpect(jsonPath("$.content[0].status").value("PAUSED"));
        mvc.perform(get("/api/schedulings").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Alpha log", "Beta report")));
        mvc.perform(get("/api/schedulings").param("search", "100%_match"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsSortingByAnUnknownPropertyAndAnUnknownStatus() throws Exception {
        mvc.perform(get("/api/schedulings").param("sort", "fingerprint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());
        mvc.perform(get("/api/schedulings").param("status", "SLEEPING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("has an invalid value"));
    }

    @Test
    void updateReplacesTheSchedulingAndReleasesTheEditLock() throws Exception {
        UUID id = createId(logScheduling("Report", "v1", cron("0 0 9 * * ?")));
        String token = acquireLock(id, "tab-A");
        Map<String, Object> replacement = logScheduling("Report v2", "v2",
                weekly(List.of("MON", "WED"), "10:30", "Asia/Jerusalem"));
        replacement.put("version", 0);

        send(put("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, token), replacement)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Report v2"))
                .andExpect(jsonPath("$.parameters.message").value("v2"))
                .andExpect(jsonPath("$.timing.type").value("WEEKLY"))
                .andExpect(jsonPath("$.timing.days").value(contains("MON", "WED")))
                .andExpect(jsonPath("$.timing.time").value("10:30"))
                .andExpect(jsonPath("$.timing.timezone").value("Asia/Jerusalem"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.editLock").value(nullValue()))
                .andExpect(jsonPath("$.nextFireTime").isNotEmpty());

        // The old timing rows are gone (the timing is replaced, not mutated), and the lock is free again.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cron_timings", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_timings", Integer.class)).isOne();
        acquireLock(id, "tab-B");
    }

    @Test
    void updateNeedsTheLockTokenTheLockAndTheCurrentVersion() throws Exception {
        UUID id = createId(logScheduling("Guarded", "v1", cron("0 0 9 * * ?")));
        Map<String, Object> replacement = logScheduling("Guarded v2", "v2", cron("0 0 9 * * ?"));
        replacement.put("version", 0);

        send(put("/api/schedulings/{id}", id), replacement)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['X-Edit-Lock-Token']").value("header is required"));
        send(put("/api/schedulings/{id}", id).header(EDIT_LOCK_TOKEN, UUID.randomUUID()), replacement)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCK_LOST"));

        String token = acquireLock(id, "tab-A");
        replacement.put("version", 7);
        send(put("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, token), replacement)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        replacement.remove("version");
        send(put("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, token), replacement)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").value("is required"));

        assertThat(fetch(id).get("name").asText()).isEqualTo("Guarded");
    }

    @Test
    void pauseRemovesTheScheduleAndResumeRebuildsIt() throws Exception {
        UUID id = createId(logScheduling("Pausable", "p", cron("0 0 9 * * ?")));

        mvc.perform(post("/api/schedulings/{id}/pause", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.nextFireTime").value(nullValue()));
        assertThat(hasMainTrigger(id)).isFalse();
        assertThat(hasJob(id)).isTrue(); // "run now" still works while paused
        mvc.perform(post("/api/schedulings/{id}/pause", id)).andExpect(status().isOk()); // idempotent

        mvc.perform(post("/api/schedulings/{id}/resume", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextFireTime").isNotEmpty());
        assertThat(hasMainTrigger(id)).isTrue();
        mvc.perform(post("/api/schedulings/{id}/resume", id)).andExpect(status().isOk()); // idempotent
    }

    @Test
    void createsAPausedSchedulingWithoutATrigger() throws Exception {
        Map<String, Object> request = logScheduling("Created paused", "later", cron("0 0 9 * * ?"));
        request.put("enabled", false);

        UUID id = UUID.fromString(body(send(post("/api/schedulings"), request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAUSED")))
                .get("id").asText());

        assertThat(hasJob(id)).isTrue();
        assertThat(hasMainTrigger(id)).isFalse();
    }

    @Test
    void deleteRemovesTheSchedulingItsHistoryAndItsQuartzJob() throws Exception {
        UUID id = createId(logScheduling("Short-lived", "bye", cron("0 0 9 * * ?")));
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));

        mvc.perform(delete("/api/schedulings/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/schedulings/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/schedulings/{id}/executions", id)).andExpect(status().isNotFound());
        assertThat(executions(id)).isEmpty();
        assertThat(hasJob(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_timings", Integer.class)).isZero();
    }

    @Test
    void unknownSchedulingIsNotFoundEverywhere() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/schedulings/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString(unknown.toString())));
        mvc.perform(delete("/api/schedulings/{id}", unknown)).andExpect(status().isNotFound());
        mvc.perform(post("/api/schedulings/{id}/pause", unknown)).andExpect(status().isNotFound());
        mvc.perform(post("/api/schedulings/{id}/resume", unknown)).andExpect(status().isNotFound());
        mvc.perform(post("/api/schedulings/{id}/run", unknown)).andExpect(status().isNotFound());
        mvc.perform(get("/api/schedulings/{id}/executions", unknown)).andExpect(status().isNotFound());
        mvc.perform(post("/api/schedulings/{id}/edit-lock", unknown).header(EDITOR_ID, "tab"))
                .andExpect(status().isNotFound());
        Map<String, Object> request = logScheduling("x", "y", cron("0 0 9 * * ?"));
        request.put("version", 0);
        send(put("/api/schedulings/{id}", unknown).header(EDIT_LOCK_TOKEN, UUID.randomUUID()), request)
                .andExpect(status().isNotFound());
    }

    @Test
    void executionHistoryIsPagedNewestFirstAndFilterable() throws Exception {
        UUID id = createId(logScheduling("Busy", "work", cron("0 0 9 * * ?")));
        for (int i = 0; i < 3; i++) {
            runNow(id);
            int expected = i + 1;
            await().atMost(TIMEOUT).until(() -> statuses(id).stream().filter("SUCCESS"::equals).count() == expected);
        }
        List<Execution> runs = executions(id);

        mvc.perform(get("/api/schedulings/{id}/executions", id).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(runs.get(2).id().toString()))
                .andExpect(jsonPath("$.content[1].id").value(runs.get(1).id().toString()))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].attempt").value(1))
                .andExpect(jsonPath("$.content[0].maxAttempts").value(3))
                .andExpect(jsonPath("$.content[0].executedBy").value(INSTANCE_ID))
                .andExpect(jsonPath("$.content[0].durationMs").isNumber())
                .andExpect(jsonPath("$.content[0].parameters.message").value("work"));
        mvc.perform(get("/api/schedulings/{id}/executions", id).param("status", "FAILED"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/schedulings/{id}/executions", id).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        mvc.perform(get("/api/schedulings/{id}/executions", id).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    void theListShowsTheLastRunOfEachScheduling() throws Exception {
        UUID id = createId(logScheduling("With a run", "ran", cron("0 0 9 * * ?")));
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));

        mvc.perform(get("/api/schedulings"))
                .andExpect(jsonPath("$.content[0].lastExecution.status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].lastExecution.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].lastExecution.attempt").value(1))
                .andExpect(jsonPath("$.content[0].lastExecution.maxAttempts").value(3))
                .andExpect(jsonPath("$.content[0].lastExecution.finishedAt").isNotEmpty());
    }

    @Test
    void runNowIsAcceptedImmediately() throws Exception {
        UUID id = createId(logScheduling("Quick", "now", cron("0 0 9 * * ?")));
        long start = System.nanoTime();
        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));
        // Picked up right after the request commits, not at Quartz's next idle poll (up to 30 s later).
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
