package com.memcyco.scheduler.service;

import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.TriggerType;
import com.memcyco.scheduler.job.TaskExecutionJob;
import com.memcyco.scheduler.scheduling.SchedulingEngineException;
import com.memcyco.scheduler.scheduling.TimingStrategies;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The only class that talks to Quartz.
 * <ul>
 *     <li>One durable job per scheduling: {@code schedulings/<id>}</li>
 *     <li>Main trigger built from the timing: {@code main/<id>}; absent while paused or completed</li>
 *     <li>One-shot retry triggers: {@code retry/<failed attempt id>}</li>
 *     <li>One-shot "run now" triggers: {@code manual/<random>}</li>
 * </ul>
 * Writes must run inside the caller's transaction ({@code MANDATORY}): Quartz uses the same database connection
 * (Spring's {@code LocalDataSourceJobStore}), so scheduling rows and Quartz triggers commit or roll back together.
 */
@Service
public class QuartzSchedulingGateway {

    public static final String JOB_GROUP = "schedulings";
    public static final String TRIGGER_TYPE = "triggerType";
    public static final String ATTEMPT = "attempt";
    public static final String PREVIOUS_ATTEMPT_ID = "previousAttemptId";
    static final String MAIN_GROUP = "main";
    static final String RETRY_GROUP = "retry";
    static final String MANUAL_GROUP = "manual";

    private final Scheduler scheduler;
    private final TimingStrategies timingStrategies;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public QuartzSchedulingGateway(Scheduler scheduler, TimingStrategies timingStrategies,
                                   NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.scheduler = scheduler;
        this.timingStrategies = timingStrategies;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Makes Quartz match the scheduling: a main trigger built from its timing if it is enabled and will run again;
     * no main trigger otherwise.
     *
     * @return true if a main trigger is now scheduled
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean schedule(Scheduling scheduling) {
        UUID id = scheduling.getId();
        ensureJob(id);
        TriggerBuilder<Trigger> base = TriggerBuilder.newTrigger()
                .withIdentity(mainKey(id))
                .forJob(jobKey(id))
                .usingJobData(TRIGGER_TYPE, TriggerType.SCHEDULED.name());
        Optional<Trigger> trigger = scheduling.isEnabled()
                ? timingStrategies.buildTrigger(scheduling.getTiming(), base, clock.instant())
                : Optional.empty();
        run(() -> {
            if (trigger.isEmpty()) {
                scheduler.unscheduleJob(mainKey(id));
            } else if (scheduler.checkExists(mainKey(id))) {
                scheduler.rescheduleJob(mainKey(id), trigger.get());
            } else {
                scheduler.scheduleJob(trigger.get());
            }
        });
        return trigger.isPresent();
    }

    /** Pause: no more scheduled runs (the job stays, so "run now" still works). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void unscheduleMain(UUID schedulingId) {
        run(() -> scheduler.unscheduleJob(mainKey(schedulingId)));
    }

    /** Removes the job and all its triggers (main, retries, pending run-now). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(UUID schedulingId) {
        run(() -> scheduler.deleteJob(jobKey(schedulingId)));
    }

    /** "Run now": a one-shot trigger that fires immediately. Works for paused schedulings too. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void triggerNow(UUID schedulingId) {
        ensureJob(schedulingId);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(UUID.randomUUID().toString(), MANUAL_GROUP)
                .forJob(jobKey(schedulingId))
                .usingJobData(TRIGGER_TYPE, TriggerType.MANUAL.name())
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        run(() -> scheduler.scheduleJob(trigger));
    }

    /** A durable one-shot trigger for the next attempt; survives restarts and can fire on any instance. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleRetry(UUID schedulingId, int attempt, UUID failedAttemptId, Instant fireAt) {
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(failedAttemptId.toString(), RETRY_GROUP)
                .forJob(jobKey(schedulingId))
                .usingJobData(TRIGGER_TYPE, TriggerType.RETRY.name())
                .usingJobData(ATTEMPT, String.valueOf(attempt))
                .usingJobData(PREVIOUS_ATTEMPT_ID, failedAttemptId.toString())
                .startAt(Date.from(fireAt))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        run(() -> scheduler.scheduleJob(trigger));
    }

    /** @return number of pending retry triggers removed */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelRetryTriggers(UUID schedulingId) {
        return query(() -> {
            int cancelled = 0;
            for (Trigger trigger : scheduler.getTriggersOfJob(jobKey(schedulingId))) {
                if (RETRY_GROUP.equals(trigger.getKey().getGroup()) && scheduler.unscheduleJob(trigger.getKey())) {
                    cancelled++;
                }
            }
            return cancelled;
        });
    }

    /** Next scheduled run per scheduling, read in one query (no per-row Quartz calls). */
    @Transactional(readOnly = true)
    public Map<UUID, Instant> nextFireTimes(Collection<UUID> schedulingIds) {
        if (schedulingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Instant> result = new HashMap<>();
        jdbc.query("""
                        SELECT trigger_name, next_fire_time FROM qrtz_triggers
                         WHERE sched_name = :schedName AND trigger_group = :group
                           AND trigger_name IN (:names) AND next_fire_time > 0
                        """,
                new MapSqlParameterSource("schedName", schedulerName())
                        .addValue("group", MAIN_GROUP)
                        .addValue("names", schedulingIds.stream().map(UUID::toString).toList()),
                rs -> {
                    result.put(UUID.fromString(rs.getString("trigger_name")),
                            Instant.ofEpochMilli(rs.getLong("next_fire_time")));
                });
        return result;
    }

    /** Ids of all schedulings that currently have a main trigger. */
    @Transactional(readOnly = true)
    public Set<UUID> scheduledIds() {
        List<String> names = jdbc.queryForList("""
                SELECT trigger_name FROM qrtz_triggers WHERE sched_name = :schedName AND trigger_group = :group
                """, new MapSqlParameterSource("schedName", schedulerName()).addValue("group", MAIN_GROUP),
                String.class);
        Set<UUID> ids = new HashSet<>();
        names.forEach(name -> ids.add(UUID.fromString(name)));
        return ids;
    }

    /** Ids of all schedulings that have a Quartz job. */
    @Transactional(readOnly = true)
    public Set<UUID> jobIds() {
        return query(() -> {
            Set<UUID> ids = new HashSet<>();
            scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP))
                    .forEach(key -> ids.add(UUID.fromString(key.getName())));
            return ids;
        });
    }

    private void ensureJob(UUID schedulingId) {
        JobDetail job = JobBuilder.newJob(TaskExecutionJob.class)
                .withIdentity(jobKey(schedulingId))
                .withDescription("Runs the task of scheduling " + schedulingId)
                .storeDurably()
                .requestRecovery(false) // a crashed run is detected as STUCK, not silently re-run
                .build();
        run(() -> scheduler.addJob(job, true));
    }

    private String schedulerName() {
        return query(scheduler::getSchedulerName);
    }

    static JobKey jobKey(UUID schedulingId) {
        return JobKey.jobKey(schedulingId.toString(), JOB_GROUP);
    }

    static TriggerKey mainKey(UUID schedulingId) {
        return TriggerKey.triggerKey(schedulingId.toString(), MAIN_GROUP);
    }

    @FunctionalInterface
    private interface QuartzAction {
        void run() throws SchedulerException;
    }

    @FunctionalInterface
    private interface QuartzQuery<T> {
        T get() throws SchedulerException;
    }

    private static void run(QuartzAction action) {
        query(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T query(QuartzQuery<T> query) {
        try {
            return query.get();
        } catch (SchedulerException e) {
            throw new SchedulingEngineException("Quartz operation failed: " + e.getMessage(), e);
        }
    }
}
