package com.memcyco.scheduler.service;

import com.memcyco.scheduler.job.TaskExecutionJob;
import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Map;
import java.util.UUID;

import static com.memcyco.scheduler.support.Requests.cron;
import static com.memcyco.scheduler.support.Requests.logScheduling;
import static org.assertj.core.api.Assertions.assertThat;

/** Startup reconciliation makes Quartz match the schedulings table again after Quartz data was lost or edited. */
class QuartzReconcilerIT extends IntegrationTest {

    @Autowired
    private QuartzReconciler reconciler;

    @Test
    void recreatesTheMissingTriggerOfAnActiveScheduling() throws Exception {
        UUID id = createId(logScheduling("Lost trigger", "x", cron("0 0 9 * * ?")));
        inTransaction(() -> scheduler.unscheduleJob(QuartzSchedulingGateway.mainKey(id)));
        assertThat(fetch(id).get("nextFireTime").isNull()).isTrue();

        assertThat(reconciler.reconcile()).isEqualTo(new QuartzReconciler.Result(1, 0, 0));

        assertThat(hasMainTrigger(id)).isTrue();
        assertThat(fetch(id).get("nextFireTime").isNull()).isFalse();
        assertThat(reconciler.reconcile()).isEqualTo(new QuartzReconciler.Result(0, 0, 0)); // idempotent
    }

    @Test
    void removesTheTriggerOfAPausedScheduling() throws Exception {
        Map<String, Object> request = logScheduling("Paused", "x", cron("0 0 9 * * ?"));
        request.put("enabled", false);
        UUID id = createId(request);
        inTransaction(() -> scheduler.scheduleJob(TriggerBuilder.newTrigger()
                .withIdentity(QuartzSchedulingGateway.mainKey(id))
                .forJob(QuartzSchedulingGateway.jobKey(id))
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?"))
                .build()));

        assertThat(reconciler.reconcile()).isEqualTo(new QuartzReconciler.Result(0, 1, 0));

        assertThat(hasMainTrigger(id)).isFalse();
        assertThat(hasJob(id)).isTrue();
    }

    @Test
    void deletesJobsWhoseSchedulingNoLongerExists() throws Exception {
        UUID orphan = UUID.randomUUID();
        inTransaction(() -> scheduler.addJob(JobBuilder.newJob(TaskExecutionJob.class)
                .withIdentity(QuartzSchedulingGateway.jobKey(orphan))
                .storeDurably()
                .build(), false));

        reconciler.run(new DefaultApplicationArguments());

        assertThat(hasJob(orphan)).isFalse();
    }
}
