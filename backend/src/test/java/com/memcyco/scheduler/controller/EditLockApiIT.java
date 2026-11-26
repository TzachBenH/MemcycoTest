package com.memcyco.scheduler.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.memcyco.scheduler.support.Requests.cron;
import static com.memcyco.scheduler.support.Requests.logScheduling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The edit lock: one editor (user + browser tab) at a time; others may still "run now". */
class EditLockApiIT extends IntegrationTest {

    @Test
    void acquireRenewAndRelease() throws Exception {
        UUID id = createId(logScheduling("Lockable", "x", cron("0 0 9 * * ?")));

        JsonNode acquired = body(mvc.perform(post("/api/schedulings/{id}/edit-lock", id).header(EDITOR_ID, "tab-A"))
                .andExpect(status().isOk()));
        String token = acquired.get("lockToken").asText();
        Instant expiresAt = Instant.parse(acquired.get("expiresAt").asText());
        assertThat(expiresAt).isCloseTo(Instant.now().plus(Duration.ofMinutes(2)), within(Duration.ofSeconds(30)));

        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockToken").value(token));

        mvc.perform(delete("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, token))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, token))
                .andExpect(status().isNoContent()); // idempotent
        acquireLock(id, "tab-B");
    }

    @Test
    void anotherEditorIsBlockedFromEditingDeletingPausingAndResumingButCanRunNow() throws Exception {
        UUID id = createId(logScheduling("Contested", "x", cron("0 0 9 * * ?")));
        acquireLock(id, "tab-A");

        mvc.perform(post("/api/schedulings/{id}/edit-lock", id).header(EDITOR_ID, "tab-B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCKED"))
                .andExpect(jsonPath("$.lockedBy").value("anonymous"))
                .andExpect(jsonPath("$.since").isNotEmpty());
        mvc.perform(delete("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCKED"));
        mvc.perform(post("/api/schedulings/{id}/pause", id).header(EDITOR_ID, "tab-B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCKED"));
        mvc.perform(post("/api/schedulings/{id}/resume", id).header(EDITOR_ID, "tab-B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCKED"));

        runNow(id);
        await().atMost(TIMEOUT).until(() -> statuses(id).equals(List.of("SUCCESS")));

        // The holder itself is not blocked.
        mvc.perform(post("/api/schedulings/{id}/pause", id).header(EDITOR_ID, "tab-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void responsesTellWhetherTheLockIsMine() throws Exception {
        UUID id = createId(logScheduling("Shown", "x", cron("0 0 9 * * ?")));
        acquireLock(id, "tab-A");

        mvc.perform(get("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A"))
                .andExpect(jsonPath("$.editLock.lockedBy").value("anonymous"))
                .andExpect(jsonPath("$.editLock.mine").value(true));
        mvc.perform(get("/api/schedulings").header(EDITOR_ID, "tab-B"))
                .andExpect(jsonPath("$.content[0].editLock.lockedBy").value("anonymous"))
                .andExpect(jsonPath("$.content[0].editLock.mine").value(false))
                .andExpect(jsonPath("$.content[0].editLock.expiresAt").isNotEmpty());
    }

    @Test
    void theSameTabCanReacquireAndTheOldTokenStopsWorking() throws Exception {
        UUID id = createId(logScheduling("Reopened", "x", cron("0 0 9 * * ?")));
        String first = acquireLock(id, "tab-A");
        String second = acquireLock(id, "tab-A");

        assertThat(second).isNotEqualTo(first);
        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCK_LOST"));
        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, second))
                .andExpect(status().isOk());
    }

    @Test
    void anExpiredLockCanBeTakenOverAndItsFormerHolderCannotSave() throws Exception {
        UUID id = createId(logScheduling("Abandoned", "x", cron("0 0 9 * * ?")));
        String staleToken = acquireLock(id, "tab-A");
        jdbc.update("UPDATE scheduling_edit_locks SET expires_at = now() - interval '1 second' WHERE scheduling_id = ?",
                id);

        // Expired locks are ignored everywhere ...
        mvc.perform(get("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-B"))
                .andExpect(jsonPath("$.editLock").value(nullValue()));
        // ... and can be taken over.
        acquireLock(id, "tab-B");

        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, staleToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCK_LOST"));
        Map<String, Object> edit = logScheduling("Abandoned v2", "y", cron("0 0 9 * * ?"));
        edit.put("version", 0);
        send(put("/api/schedulings/{id}", id).header(EDITOR_ID, "tab-A").header(EDIT_LOCK_TOKEN, staleToken), edit)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCK_LOST"));
    }

    @Test
    void acquiringNeedsTheEditorIdAndRenewingNeedsAValidToken() throws Exception {
        UUID id = createId(logScheduling("Headers", "x", cron("0 0 9 * * ?")));

        mvc.perform(post("/api/schedulings/{id}/edit-lock", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['X-Editor-Id']").value("header is required"));
        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_LOCK_LOST"));
        mvc.perform(put("/api/schedulings/{id}/edit-lock", id).header(EDIT_LOCK_TOKEN, "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
