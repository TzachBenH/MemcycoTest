package com.memcyco.scheduler.service;

import com.memcyco.scheduler.config.ExecutionProperties;
import com.memcyco.scheduler.repository.EditLockRepository;
import com.memcyco.scheduler.repository.TaskExecutionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/** Work done by the internal system jobs. */
@Service
public class ExecutionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionMaintenanceService.class);
    static final int DELETE_BATCH_SIZE = 5_000;

    private final TaskExecutionWriter executions;
    private final EditLockRepository editLocks;
    private final ExecutionProperties properties;
    private final TransactionTemplate transactions;

    public ExecutionMaintenanceService(TaskExecutionWriter executions, EditLockRepository editLocks,
                                       ExecutionProperties properties, TransactionTemplate transactions) {
        this.executions = executions;
        this.editLocks = editLocks;
        this.properties = properties;
        this.transactions = transactions;
    }

    /** RUNNING executions without a heartbeat for longer than the timeout become STUCK (releasing their lock). */
    public int markStuckExecutions() {
        Duration timeout = properties.stuckTimeout();
        Integer stuck = transactions.execute(status ->
                executions.markStuck(timeout, "No heartbeat for more than " + describe(timeout)));
        int count = stuck == null ? 0 : stuck;
        if (count > 0) {
            log.warn("Marked {} execution(s) as STUCK (no heartbeat for more than {})", count, describe(timeout));
        }
        return count;
    }

    /** Deletes history older than the retention period, in small batches (short transactions), and expired locks. */
    public int purgeOldHistory() {
        int total = 0;
        int deleted;
        do {
            Integer batch = transactions.execute(status ->
                    executions.deleteFinishedOlderThan(properties.retention(), DELETE_BATCH_SIZE));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == DELETE_BATCH_SIZE);
        Integer locks = transactions.execute(status -> editLocks.deleteExpired());
        log.info("History retention: deleted {} execution(s) older than {}, {} expired edit lock(s)", total,
                describe(properties.retention()), locks);
        return total;
    }

    static String describe(Duration duration) {
        if (duration.toDaysPart() > 0 && duration.equals(Duration.ofDays(duration.toDays()))) {
            return duration.toDays() + " days";
        }
        if (duration.toSecondsPart() == 0) {
            return duration.toMinutes() + " minutes";
        }
        return duration.toSeconds() + " seconds";
    }
}
