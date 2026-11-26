package com.memcyco.scheduler.job;

import com.memcyco.scheduler.service.ExecutionMaintenanceService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/** System job (daily, on one cluster node): deletes execution history older than the retention period. */
@DisallowConcurrentExecution
public class HistoryRetentionJob implements Job {

    private final ExecutionMaintenanceService maintenance;

    public HistoryRetentionJob(ExecutionMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public void execute(JobExecutionContext context) {
        maintenance.purgeOldHistory();
    }
}
