package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.dto.EditLockResponse;
import com.memcyco.scheduler.service.EditLockService;
import com.memcyco.scheduler.service.SchedulingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Exclusive edit lock: acquire when opening the edit form, renew every 30s, release on close. */
@RestController
@RequestMapping("/api/schedulings/{id}/edit-lock")
@Tag(name = "Edit lock", description = "Only one editor (user + browser tab) can edit a scheduling at a time")
public class EditLockController {

    private final EditLockService editLocks;
    private final SchedulingQueryService queries;

    public EditLockController(EditLockService editLocks, SchedulingQueryService queries) {
        this.editLocks = editLocks;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Acquire the edit lock", description = "409 EDIT_LOCKED (lockedBy, since) if held by another editor")
    public EditLockResponse acquire(@PathVariable UUID id, @RequestHeader(ApiHeaders.EDITOR_ID) String editorId) {
        queries.findOrThrow(id);
        return editLocks.acquire(id, editorId);
    }

    @PutMapping
    @Operation(summary = "Renew the edit lock", description = "409 EDIT_LOCK_LOST if it expired or was taken over")
    public EditLockResponse renew(@PathVariable UUID id, @RequestHeader(ApiHeaders.EDIT_LOCK_TOKEN) UUID token) {
        return editLocks.renew(id, token);
    }

    @DeleteMapping
    @Operation(summary = "Release the edit lock (idempotent)")
    public ResponseEntity<Void> release(@PathVariable UUID id,
                                        @RequestHeader(ApiHeaders.EDIT_LOCK_TOKEN) UUID token) {
        editLocks.release(id, token);
        return ResponseEntity.noContent().build();
    }
}
