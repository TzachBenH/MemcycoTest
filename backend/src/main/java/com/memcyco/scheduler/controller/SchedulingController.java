package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.dto.PageResponse;
import com.memcyco.scheduler.dto.SchedulingRequest;
import com.memcyco.scheduler.dto.SchedulingResponse;
import com.memcyco.scheduler.dto.SchedulingStatus;
import com.memcyco.scheduler.exception.ValidationException;
import com.memcyco.scheduler.service.SchedulingQueryService;
import com.memcyco.scheduler.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedulings")
@Tag(name = "Schedulings", description = "Create and manage scheduled executions of predefined tasks")
public class SchedulingController {

    private static final Set<String> SORTABLE = Set.of("name", "taskKey", "enabled", "createdAt", "updatedAt");

    private final SchedulingService service;
    private final SchedulingQueryService queries;

    public SchedulingController(SchedulingService service, SchedulingQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "List schedulings (paged, sortable, searchable by name, filterable by task and status)")
    public PageResponse<SchedulingResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String taskKey,
            @RequestParam(required = false) SchedulingStatus status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false) String editorId) {
        return queries.list(search, taskKey, status, withStableOrder(pageable), editorId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one scheduling")
    public SchedulingResponse get(@PathVariable UUID id,
                                  @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false) String editorId) {
        return queries.get(id, editorId);
    }

    @PostMapping
    @Operation(summary = "Create a scheduling",
            description = "400 VALIDATION_FAILED with per-field errors; 409 DUPLICATE_SCHEDULING if an identical "
                    + "active scheduling exists")
    public ResponseEntity<SchedulingResponse> create(
            @RequestBody SchedulingRequest request,
            @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false) String editorId) {
        SchedulingResponse created = service.create(request, editorId);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a scheduling",
            description = "Requires the edit lock token and the version that was loaded. 409 EDIT_LOCK_LOST, "
                    + "CONCURRENT_MODIFICATION or DUPLICATE_SCHEDULING. Releases the edit lock on success.")
    public SchedulingResponse update(@PathVariable UUID id, @RequestBody SchedulingRequest request,
                                     @RequestHeader(ApiHeaders.EDIT_LOCK_TOKEN) UUID lockToken,
                                     @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false)
                                     String editorId) {
        return service.update(id, request, lockToken, editorId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a scheduling (and its history)", description = "409 EDIT_LOCKED while being edited")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false)
                                       String editorId) {
        service.delete(id, editorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a scheduling", description = "409 EDIT_LOCKED while being edited")
    public SchedulingResponse pause(@PathVariable UUID id,
                                    @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false) String editorId) {
        return service.pause(id, editorId);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused scheduling",
            description = "409 DUPLICATE_SCHEDULING if an identical scheduling is active; 409 EDIT_LOCKED")
    public SchedulingResponse resume(@PathVariable UUID id,
                                     @RequestHeader(value = ApiHeaders.EDITOR_ID, required = false)
                                     String editorId) {
        return service.resume(id, editorId);
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Run now",
            description = "Runs the task once, immediately, with the same rules as a scheduled run. "
                    + "Allowed for paused schedulings and while someone is editing. 202: the run was started; "
                    + "its result appears in the history.")
    public ResponseEntity<Void> runNow(@PathVariable UUID id) {
        service.runNow(id);
        return ResponseEntity.accepted().build();
    }

    /** Only whitelisted properties may be sorted on; the id is appended so paging is stable. */
    private static Pageable withStableOrder(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw ValidationException.of("sort", "can be one of " + String.join(", ", SORTABLE));
            }
        });
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
    }
}
