package com.memcyco.scheduler.entity.timing;

import com.memcyco.scheduler.entity.ScheduleType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Runs on the selected days of the week at {@code timeOfDay} (in the timing's timezone). */
@Entity
@Table(name = "weekly_timings")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("WEEKLY")
public class WeeklyTiming extends ScheduleTiming {

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    /** One row per selected day in {@code weekly_timing_days}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "weekly_timing_days", joinColumns = @JoinColumn(name = "timing_id"))
    @Column(name = "day_of_week", nullable = false, length = 3)
    @Convert(converter = DayOfWeekConverter.class)
    private Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);

    protected WeeklyTiming() {
        // for JPA
    }

    public WeeklyTiming(Set<DayOfWeek> days, LocalTime timeOfDay, String timezone) {
        super(timezone);
        this.days = EnumSet.copyOf(days);
        this.timeOfDay = timeOfDay;
    }

    @Override
    public ScheduleType getType() {
        return ScheduleType.WEEKLY;
    }

    public LocalTime getTimeOfDay() {
        return timeOfDay;
    }

    public Set<DayOfWeek> getDays() {
        return Collections.unmodifiableSet(days);
    }
}
