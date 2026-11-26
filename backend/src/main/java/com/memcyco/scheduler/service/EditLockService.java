package com.memcyco.scheduler.service;

import com.memcyco.scheduler.dto.EditLockInfo;
import com.memcyco.scheduler.dto.EditLockResponse;
import com.memcyco.scheduler.exception.EditLockLostException;
import com.memcyco.scheduler.exception.EditLockedException;
import com.memcyco.scheduler.repository.EditLock;
import com.memcyco.scheduler.repository.EditLockRepository;
import com.memcyco.scheduler.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exclusive edit lock (lease): only one editor (user + browser tab) may edit a scheduling at a time.
 * The editor renews the lock every 30 seconds; without renewal it expires after {@link #TTL} and can be taken over.
 * While someone else holds it, Edit / Delete / Pause / Resume are blocked; Run now is allowed.
 */
@Service
@Transactional
public class EditLockService {

    public static final Duration TTL = Duration.ofMinutes(2);
    private static final String UNKNOWN_EDITOR = "unknown";

    private final EditLockRepository locks;
    private final CurrentUserProvider currentUser;

    public EditLockService(EditLockRepository locks, CurrentUserProvider currentUser) {
        this.locks = locks;
        this.currentUser = currentUser;
    }

    /** Caller must ensure the scheduling exists. */
    public EditLockResponse acquire(UUID schedulingId, String editorId) {
        return locks.tryAcquire(schedulingId, UUID.randomUUID(), owner(editorId), TTL)
                .map(lock -> new EditLockResponse(lock.lockToken(), lock.expiresAt()))
                .orElseThrow(() -> lockedBySomeoneElse(schedulingId));
    }

    public EditLockResponse renew(UUID schedulingId, UUID token) {
        return locks.renew(schedulingId, token, TTL)
                .map(lock -> new EditLockResponse(lock.lockToken(), lock.expiresAt()))
                .orElseThrow(EditLockLostException::new);
    }

    /** Idempotent. */
    public void release(UUID schedulingId, UUID token) {
        locks.release(schedulingId, token);
    }

    /** Saving requires holding the lock. */
    @Transactional(readOnly = true)
    public void assertHeld(UUID schedulingId, UUID token) {
        if (token == null || !locks.isHeld(schedulingId, token)) {
            throw new EditLockLostException();
        }
    }

    /** Delete / pause / resume are blocked while another editor holds the lock. */
    @Transactional(readOnly = true)
    public void assertNotLockedByOthers(UUID schedulingId, String editorId) {
        locks.findActive(schedulingId)
                .filter(lock -> !lock.lockedBy().equals(owner(editorId)))
                .ifPresent(lock -> {
                    throw new EditLockedException(displayName(lock), lock.acquiredAt());
                });
    }

    @Transactional(readOnly = true)
    public Optional<EditLockInfo> activeLock(UUID schedulingId, String editorId) {
        return locks.findActive(schedulingId).map(lock -> info(lock, editorId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, EditLockInfo> activeLocks(Collection<UUID> schedulingIds, String editorId) {
        return locks.findActive(schedulingIds).values().stream()
                .collect(Collectors.toMap(EditLock::schedulingId, lock -> info(lock, editorId)));
    }

    private EditLockedException lockedBySomeoneElse(UUID schedulingId) {
        return locks.findActive(schedulingId)
                .map(lock -> new EditLockedException(displayName(lock), lock.acquiredAt()))
                // The other lock expired between the attempt and this read: treat as a lost race.
                .orElseThrow(EditLockLostException::new);
    }

    private EditLockInfo info(EditLock lock, String editorId) {
        return new EditLockInfo(displayName(lock), lock.acquiredAt(), lock.expiresAt(),
                lock.lockedBy().equals(owner(editorId)));
    }

    /** One owner per user and browser tab, so two tabs of the same user also block each other. */
    private String owner(String editorId) {
        String tab = editorId == null || editorId.isBlank() ? UNKNOWN_EDITOR : editorId.strip();
        return currentUser.currentUsername() + ":" + tab;
    }

    private static String displayName(EditLock lock) {
        int separator = lock.lockedBy().lastIndexOf(':');
        return separator > 0 ? lock.lockedBy().substring(0, separator) : lock.lockedBy();
    }
}
