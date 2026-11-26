package com.memcyco.scheduler.job;

import com.memcyco.scheduler.config.ExecutionProperties;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.TimeZone;

/**
 * Registers the internal system jobs in Quartz at startup (idempotent; replaces existing definitions).
 * They live in the "system" group, so they never appear as user schedulings, and with the clustered job store each
 * run happens on exactly one backend instance.
 */
@Component
public class SystemJobsInitializer implements ApplicationRunner {

    public static final String SYSTEM_GROUP = "system";
    private static final Logger log = LoggerFactory.getLogger(SystemJobsInitializer.class);

    private final Scheduler scheduler;
    private final ExecutionProperties properties;
    private final TransactionTemplate transactions;

    public SystemJobsInitializer(Scheduler scheduler, ExecutionProperties properties,
                                 TransactionTemplate transactions) {
        this.scheduler = scheduler;
        this.properties = properties;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            register(StuckExecutionMonitorJob.class, "stuck-execution-monitor", SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds(properties.stuckCheckInterval().toMillis())
                    .repeatForever()
                    .withMisfireHandlingInstructionNextWithRemainingCount());
            register(HistoryRetentionJob.class, "history-retention", CronScheduleBuilder
                    .cronSchedule(properties.retentionCron())
                    .inTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC))
                    .withMisfireHandlingInstructionDoNothing());
        });
        log.info("System jobs registered: stuck-execution-monitor (every {}), history-retention ({} UTC)",
                properties.stuckCheckInterval(), properties.retentionCron());
    }

    private <T extends Trigger> void register(Class<? extends Job> jobClass, String name,
                                              ScheduleBuilder<T> schedule) {
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(name, SYSTEM_GROUP).storeDurably().build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity(name, SYSTEM_GROUP).forJob(job)
                .withSchedule(schedule).build();
        try {
            scheduler.scheduleJob(job, Set.of(trigger), true);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Could not register system job " + name, e);
        }
    }
}
