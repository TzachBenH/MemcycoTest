package com.memcyco.scheduler.job;

import com.memcyco.scheduler.service.ExecutionMaintenanceService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/** System job (every minute, on one cluster node): RUNNING executions without heartbeat → STUCK. */
@DisallowConcurrentExecution
public class StuckExecutionMonitorJob implements Job {

    private final ExecutionMaintenanceService maintenance;

    public StuckExecutionMonitorJob(ExecutionMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public void execute(JobExecutionContext context) {
        maintenance.markStuckExecutions();
    }
}
