package com.memcyco.scheduler.job;

import com.memcyco.scheduler.entity.TriggerType;
import com.memcyco.scheduler.service.ExecutionRequest;
import com.memcyco.scheduler.service.ExecutionRunner;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.UUID;

import static com.memcyco.scheduler.service.QuartzSchedulingGateway.ATTEMPT;
import static com.memcyco.scheduler.service.QuartzSchedulingGateway.PREVIOUS_ATTEMPT_ID;
import static com.memcyco.scheduler.service.QuartzSchedulingGateway.TRIGGER_TYPE;

/**
 * The single Quartz job class for every scheduling. It only translates the fired trigger into an
 * {@link ExecutionRequest}; the work is done by {@link ExecutionRunner}.
 * <p>
 * Deliberately <b>not</b> {@code @DisallowConcurrentExecution}: that would make overlapping runs wait silently.
 * Overlaps are instead handled by the runtime lock, which records them as SKIPPED.
 */
public class TaskExecutionJob implements Job {

    private final ExecutionRunner runner;

    public TaskExecutionJob(ExecutionRunner runner) {
        this.runner = runner;
    }

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        UUID schedulingId = UUID.fromString(context.getJobDetail().getKey().getName());
        TriggerType triggerType = data.containsKey(TRIGGER_TYPE)
                ? TriggerType.valueOf(data.getString(TRIGGER_TYPE))
                : TriggerType.SCHEDULED;
        int attempt = data.containsKey(ATTEMPT) ? Integer.parseInt(data.getString(ATTEMPT)) : 1;
        UUID previousAttemptId = data.containsKey(PREVIOUS_ATTEMPT_ID)
                ? UUID.fromString(data.getString(PREVIOUS_ATTEMPT_ID))
                : null;
        runner.run(new ExecutionRequest(schedulingId, triggerType, attempt, previousAttemptId));
    }
}
