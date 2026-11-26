package com.memcyco.scheduler.service;

import com.memcyco.scheduler.dto.SchedulingRequest;
import com.memcyco.scheduler.dto.SchedulingResponse;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.exception.ConcurrentModificationException;
import com.memcyco.scheduler.exception.DuplicateSchedulingException;
import com.memcyco.scheduler.exception.ValidationException;
import com.memcyco.scheduler.repository.SchedulingRepository;
import com.memcyco.scheduler.validation.SchedulingRequestValidator;
import com.memcyco.scheduler.validation.ValidatedScheduling;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write side: one use case per method, each in one transaction.
 * Rules enforced here: validation (all errors at once), no active duplicates, edit lock, optimistic locking.
 * Quartz is updated in the same transaction, so the table and the scheduler never disagree.
 */
@Service
@Transactional
public class SchedulingService {

    private static final String ACTIVE_FINGERPRINT_INDEX = "ux_schedulings_active_fingerprint";

    private final SchedulingRepository schedulings;
    private final SchedulingQueryService queries;
    private final SchedulingRequestValidator validator;
    private final FingerprintService fingerprints;
    private final EditLockService editLocks;
    private final QuartzSchedulingGateway quartz;
    private final RetryService retries;

    public SchedulingService(SchedulingRepository schedulings, SchedulingQueryService queries,
                             SchedulingRequestValidator validator, FingerprintService fingerprints,
                             EditLockService editLocks, QuartzSchedulingGateway quartz, RetryService retries) {
        this.schedulings = schedulings;
        this.queries = queries;
        this.validator = validator;
        this.fingerprints = fingerprints;
        this.editLocks = editLocks;
        this.quartz = quartz;
        this.retries = retries;
    }

    public SchedulingResponse create(SchedulingRequest request, String editorId) {
        ValidatedScheduling valid = validator.validate(request, null);
        String fingerprint = fingerprints.fingerprint(valid.taskKey(), valid.parameters(), valid.timing());
        Scheduling scheduling = new Scheduling(valid.name(), valid.taskKey(), valid.parameters(), valid.enabled(),
                valid.timing(), fingerprint);
        if (scheduling.isEnabled()) {
            assertNoActiveDuplicate(fingerprint, scheduling.getId());
        }
        Scheduling saved = saveChecked(scheduling);
        quartz.schedule(saved);
        return queries.toDetailedResponse(saved, editorId);
    }

    /** Full replacement. Requires the edit lock and the version the editor loaded. Releases the lock. */
    public SchedulingResponse update(UUID id, SchedulingRequest request, UUID lockToken, String editorId) {
        Scheduling scheduling = queries.findOrThrow(id);
        editLocks.assertHeld(id, lockToken);
        if (request.version() == null) {
            throw ValidationException.of("version", "is required");
        }
        if (!request.version().equals(scheduling.getVersion())) {
            throw new ConcurrentModificationException();
        }
        ValidatedScheduling valid = validator.validate(request, scheduling.getTiming());
        String fingerprint = fingerprints.fingerprint(valid.taskKey(), valid.parameters(), valid.timing());
        // Check before changing the entity: the check's query would otherwise auto-flush the change first and hit
        // the unique index outside saveChecked.
        if (valid.enabled()) {
            assertNoActiveDuplicate(fingerprint, id);
        }
        scheduling.update(valid.name(), valid.taskKey(), valid.parameters(), valid.enabled(), valid.timing(),
                fingerprint);
        Scheduling saved = saveChecked(scheduling);
        retries.cancelPending(id, RetryCancelledReason.EDITED);
        quartz.schedule(saved);
        editLocks.release(id, lockToken);
        return queries.toDetailedResponse(saved, editorId);
    }

    public void delete(UUID id, String editorId) {
        Scheduling scheduling = queries.findOrThrow(id);
        editLocks.assertNotLockedByOthers(id, editorId);
        schedulings.delete(scheduling);
        schedulings.flush();
        quartz.delete(id); // removes the main trigger, pending retries and pending run-now triggers
    }

    public SchedulingResponse pause(UUID id, String editorId) {
        Scheduling scheduling = queries.findOrThrow(id);
        editLocks.assertNotLockedByOthers(id, editorId);
        if (scheduling.isEnabled()) {
            scheduling.setEnabled(false);
            saveChecked(scheduling);
            retries.cancelPending(id, RetryCancelledReason.PAUSED);
            quartz.unscheduleMain(id);
        }
        return queries.toDetailedResponse(scheduling, editorId);
    }

    /** Blocked (409 DUPLICATE_SCHEDULING) if an identical scheduling is active. */
    public SchedulingResponse resume(UUID id, String editorId) {
        Scheduling scheduling = queries.findOrThrow(id);
        editLocks.assertNotLockedByOthers(id, editorId);
        if (!scheduling.isEnabled()) {
            assertNoActiveDuplicate(scheduling.getFingerprint(), id);
            scheduling.setEnabled(true);
            saveChecked(scheduling);
            quartz.schedule(scheduling); // rebuilt from the timing: runs missed while paused are skipped
        }
        return queries.toDetailedResponse(scheduling, editorId);
    }

    /**
     * Runs the scheduling once, now, with the same rules as a scheduled run (runtime lock, retries, heartbeat).
     * Allowed for paused schedulings and while someone is editing.
     */
    public void runNow(UUID id) {
        queries.findOrThrow(id);
        quartz.triggerNow(id);
    }

    /** Must run before the entity is changed (see update). */
    private void assertNoActiveDuplicate(String fingerprint, UUID schedulingId) {
        schedulings.findFirstByFingerprintAndEnabledTrueAndIdNot(fingerprint, schedulingId)
                .ifPresent(existing -> {
                    throw new DuplicateSchedulingException(existing.getId(), existing.getName());
                });
    }

    /** The partial unique index is the safety net for two identical saves racing past the check above. */
    private Scheduling saveChecked(Scheduling scheduling) {
        try {
            return schedulings.saveAndFlush(scheduling);
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (cause.contains(ACTIVE_FINGERPRINT_INDEX)) {
                throw new DuplicateSchedulingException(null, null);
            }
            throw e;
        }
    }
}
