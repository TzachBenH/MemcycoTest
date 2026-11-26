package com.memcyco.scheduler.entity;

import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A scheduled execution of a predefined task: which task, with which parameter values, and when (its timing).
 * One row per scheduling = one row in the UI table.
 */
@Entity
@Table(name = "schedulings")
@EntityListeners(AuditingEntityListener.class)
public class Scheduling {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Key of a task defined in code (see {@code task.TaskRegistry}). */
    @Column(name = "task_key", nullable = false, length = 50)
    private String taskKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean enabled;

    /** Composition: the timing is replaced as a whole on edit; orphan removal deletes the old timing rows. */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "timing_id", nullable = false, unique = true)
    private ScheduleTiming timing;

    /** Hash of task + canonical parameters + canonical timing; unique among enabled schedulings. */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    /** Optimistic locking: a stale version on update results in 409. */
    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected Scheduling() {
        // for JPA
    }

    public Scheduling(String name, String taskKey, Map<String, Object> parameters, boolean enabled,
                      ScheduleTiming timing, String fingerprint) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.taskKey = taskKey;
        this.parameters = new LinkedHashMap<>(parameters);
        this.enabled = enabled;
        this.timing = timing;
        this.fingerprint = fingerprint;
    }

    /** Full replacement (PUT semantics). The timing object is always replaced, never mutated. */
    public void update(String name, String taskKey, Map<String, Object> parameters, boolean enabled,
                       ScheduleTiming timing, String fingerprint) {
        this.name = name;
        this.taskKey = taskKey;
        this.parameters = new LinkedHashMap<>(parameters);
        this.enabled = enabled;
        this.timing = timing;
        this.fingerprint = fingerprint;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ScheduleTiming getTiming() {
        return timing;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
