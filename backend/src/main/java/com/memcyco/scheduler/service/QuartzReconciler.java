package com.memcyco.scheduler.service;

import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.repository.SchedulingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Startup safety net: makes Quartz match the schedulings table. Normally nothing changes (both are always written
 * in one transaction), but if Quartz data was lost or edited by hand, the system repairs itself:
 * <ul>
 *     <li>enabled scheduling without its main trigger → trigger recreated (unless it will never run again)</li>
 *     <li>paused scheduling with a main trigger → trigger removed</li>
 *     <li>Quartz job whose scheduling no longer exists → job deleted</li>
 * </ul>
 * Idempotent, so it is safe when several instances start at the same time.
 */
@Component
public class QuartzReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuartzReconciler.class);
    private static final int PAGE_SIZE = 500;

    private final SchedulingRepository schedulings;
    private final QuartzSchedulingGateway quartz;
    private final TransactionTemplate transactions;

    public QuartzReconciler(SchedulingRepository schedulings, QuartzSchedulingGateway quartz,
                            TransactionTemplate transactions) {
        this.schedulings = schedulings;
        this.quartz = quartz;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    public Result reconcile() {
        Set<UUID> scheduled = quartz.scheduledIds();
        AtomicInteger recreated = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        int page = 0;
        boolean more = true;
        while (more) {
            PageRequest request = PageRequest.of(page++, PAGE_SIZE, Sort.by("id"));
            Page<Scheduling> batch = transactions.execute(status -> {
                Page<Scheduling> result = schedulings.findAll((root, query, cb) -> null, request);
                for (Scheduling s : result) {
                    boolean hasTrigger = scheduled.contains(s.getId());
                    if (s.isEnabled() && !hasTrigger && quartz.schedule(s)) {
                        recreated.incrementAndGet();
                    } else if (!s.isEnabled() && hasTrigger) {
                        quartz.unscheduleMain(s.getId());
                        removed.incrementAndGet();
                    }
                }
                return result;
            });
            more = batch != null && batch.hasNext();
        }

        AtomicInteger orphans = new AtomicInteger();
        transactions.executeWithoutResult(status -> quartz.jobIds().stream()
                .filter(id -> !schedulings.existsById(id))
                .forEach(id -> {
                    quartz.delete(id);
                    orphans.incrementAndGet();
                }));

        Result result = new Result(recreated.get(), removed.get(), orphans.get());
        if (result.changed()) {
            log.warn("Quartz reconciliation repaired: {} trigger(s) recreated, {} removed, {} orphan job(s) deleted",
                    result.recreated(), result.removed(), result.orphansDeleted());
        } else {
            log.info("Quartz reconciliation: Quartz matches the schedulings table");
        }
        return result;
    }

    public record Result(int recreated, int removed, int orphansDeleted) {
        boolean changed() {
            return recreated + removed + orphansDeleted > 0;
        }
    }
}
