package com.memcyco.scheduler.repository;

import com.memcyco.scheduler.entity.Scheduling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchedulingRepository extends JpaRepository<Scheduling, UUID>, JpaSpecificationExecutor<Scheduling> {

    /** Loads the timing in the same query (no N+1); weekly days are batch-fetched. */
    @Override
    @EntityGraph(attributePaths = "timing")
    Page<Scheduling> findAll(Specification<Scheduling> specification, Pageable pageable);

    /** The active scheduling that a new/changed/resumed scheduling would duplicate. */
    Optional<Scheduling> findFirstByFingerprintAndEnabledTrueAndIdNot(String fingerprint, UUID id);

    /** All other schedulings with the same fingerprint (for warnings). */
    List<Scheduling> findByFingerprintAndIdNot(String fingerprint, UUID id);
}
