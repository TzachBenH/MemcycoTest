package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.memcyco.scheduler.support.Requests.cron;
import static com.memcyco.scheduler.support.Requests.logScheduling;
import static com.memcyco.scheduler.support.Requests.scheduling;
import static com.memcyco.scheduler.support.Requests.weekly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duplicate prevention: two <b>active</b> schedulings may not run the same task with the same parameters on the same
 * schedule — compared by meaning, not by text.
 */
class DuplicateSchedulingIT extends IntegrationTest {

    @Test
    void aWeeklyTimingAndTheEquivalentCronAreTheSameSchedule() throws Exception {
        UUID weekly = createId(logScheduling("Weekly", "hello", weekly(List.of("MON"), "09:00", "Asia/Jerusalem")));

        send(post("/api/schedulings"), logScheduling("Cron", "hello", cron("0 0 9 ? * MON", "Asia/Jerusalem")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCHEDULING"))
                .andExpect(jsonPath("$.conflictingScheduling.id").value(weekly.toString()))
                .andExpect(jsonPath("$.conflictingScheduling.name").value("Weekly"));
    }

    @Test
    void parameterDefaultsWhitespaceKeyOrderAndCronSpellingDoNotMakeADifference() throws Exception {
        createId(logScheduling("First", "hello", cron("0 15 10 * * ?")));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("level", "INFO");
        parameters.put("message", "  hello ");

        send(post("/api/schedulings"), scheduling("Second", "LOG", parameters, cron("0 15 10 ? * MON-SUN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCHEDULING"));
    }

    @Test
    void differentParametersTimesOrTimezonesAreDifferentSchedulings() throws Exception {
        createId(logScheduling("A", "hello", cron("0 0 9 * * ?")));
        createId(logScheduling("B", "hello again", cron("0 0 9 * * ?")));
        createId(logScheduling("C", "hello", cron("0 0 10 * * ?")));
        createId(logScheduling("D", "hello", cron("0 0 9 * * ?", "Asia/Jerusalem")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedulings", Integer.class)).isEqualTo(4);
    }

    @Test
    void aDuplicateOfAPausedSchedulingIsAllowedWithAWarningButBlocksResumingThePausedOne() throws Exception {
        UUID original = createId(logScheduling("Original", "hello", cron("0 0 9 * * ?")));
        pause(original);

        UUID copy = UUID.fromString(body(send(post("/api/schedulings"),
                logScheduling("Copy", "hello", cron("0 0 9 * * ?")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0].code").value("PAUSED_DUPLICATE_EXISTS"))
                .andExpect(jsonPath("$.warnings[0].schedulingId").value(original.toString()))
                .andExpect(jsonPath("$.warnings[0].schedulingName").value("Original")))
                .get("id").asText());

        mvc.perform(post("/api/schedulings/{id}/resume", original))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCHEDULING"))
                .andExpect(jsonPath("$.conflictingScheduling.id").value(copy.toString()))
                .andExpect(jsonPath("$.conflictingScheduling.name").value("Copy"));
        assertThat(fetch(original).get("status").asText()).isEqualTo("PAUSED");
        assertThat(fetch(original).get("warnings").get(0).get("code").asText()).isEqualTo("ACTIVE_DUPLICATE_EXISTS");

        pause(copy);
        resume(original);
        assertThat(fetch(original).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void editingASchedulingIntoADuplicateIsRejected() throws Exception {
        createId(logScheduling("Existing", "same", cron("0 0 9 * * ?")));
        UUID other = createId(logScheduling("Other", "different", cron("0 0 9 * * ?")));
        String token = acquireLock(other, "tab-A");
        Map<String, Object> intoDuplicate = logScheduling("Other", "same", cron("0 0 9 * * ?"));
        intoDuplicate.put("version", 0);

        send(put("/api/schedulings/{id}", other).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, token),
                intoDuplicate)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCHEDULING"))
                .andExpect(jsonPath("$.conflictingScheduling.name").value("Existing"));

        assertThat(fetch(other).get("parameters").get("message").asText()).isEqualTo("different");
    }

    @Test
    void identicalCreatesRacingInParallelCreateExactlyOneScheduling() throws Exception {
        int requests = 8;
        String request = json(logScheduling("Race", "same", cron("0 0 4 * * ?")));
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/schedulings").contentType(MediaType.APPLICATION_JSON)
                            .content(request)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(requests - 1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM schedulings", Integer.class)).isOne();
        } finally {
            pool.shutdownNow();
        }
    }
}
