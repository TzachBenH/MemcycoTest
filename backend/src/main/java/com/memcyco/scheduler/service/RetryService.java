package com.memcyco.scheduler.service;

import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.repository.TaskExecutionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Pending retries: scheduling them, and cancelling them (superseded by a new run, pause, edit). */
@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    private final TaskExecutionWriter executions;
    private final QuartzSchedulingGateway quartz;

    public RetryService(TaskExecutionWriter executions, QuartzSchedulingGateway quartz) {
        this.executions = executions;
        this.quartz = quartz;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleRetry(UUID schedulingId, int nextAttempt, UUID failedAttemptId, Instant fireAt) {
        quartz.scheduleRetry(schedulingId, nextAttempt, failedAttemptId, fireAt);
    }

    /** Cancels the scheduling's pending retry (if any) and records why on the failed attempt. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPending(UUID schedulingId, RetryCancelledReason reason) {
        // Rows first, then Quartz: the same lock order as recording a failure (avoids deadlocks).
        int attempts = executions.cancelPendingRetries(schedulingId, reason);
        int triggers = quartz.cancelRetryTriggers(schedulingId);
        if (attempts > 0 || triggers > 0) {
            log.info("Cancelled pending retry of scheduling {} ({})", schedulingId, reason);
        }
    }
}
