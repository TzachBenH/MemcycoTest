package com.memcyco.scheduler.repository;

import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.TaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, UUID> {

    /** Latest attempt per scheduling, for a whole page of schedulings in one query. */
    @Query(value = """
            SELECT DISTINCT ON (scheduling_id) *
            FROM task_executions
            WHERE scheduling_id IN (:ids)
            ORDER BY scheduling_id, started_at DESC
            """, nativeQuery = true)
    List<TaskExecution> findLatestBySchedulingIds(@Param("ids") Collection<UUID> schedulingIds);

    Page<TaskExecution> findBySchedulingId(UUID schedulingId, Pageable pageable);

    Page<TaskExecution> findBySchedulingIdAndStatus(UUID schedulingId, ExecutionStatus status, Pageable pageable);
}
