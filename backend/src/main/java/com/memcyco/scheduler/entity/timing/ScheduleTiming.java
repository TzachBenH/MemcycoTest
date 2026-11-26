package com.memcyco.scheduler.entity.timing;

import com.memcyco.scheduler.entity.ScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.ZoneId;
import java.util.UUID;

/**
 * When a scheduling runs. Base of a JPA {@code JOINED} hierarchy: this table holds what every timing shares
 * (type + timezone); each subclass has its own table with only its own, non-nullable columns.
 * <p>
 * A timing is immutable once created: editing a scheduling's timing replaces the whole timing object
 * (orphan removal deletes the old rows), so the scheduling keeps its id and history.
 */
@Entity
@Table(name = "schedule_timings")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class ScheduleTiming {

    @Id
    private UUID id;

    /** IANA zone id, e.g. "Asia/Jerusalem". Weekly/cron times are interpreted in this zone. */
    @Column(nullable = false, length = 50)
    private String timezone;

    protected ScheduleTiming() {
        // for JPA
    }

    protected ScheduleTiming(String timezone) {
        this.id = UUID.randomUUID();
        this.timezone = timezone;
    }

    public abstract ScheduleType getType();

    public UUID getId() {
        return id;
    }

    public String getTimezone() {
        return timezone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
