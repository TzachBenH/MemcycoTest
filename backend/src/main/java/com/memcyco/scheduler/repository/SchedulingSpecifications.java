package com.memcyco.scheduler.repository;

import com.memcyco.scheduler.dto.SchedulingStatus;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search and filters for the schedulings table. */
public final class SchedulingSpecifications {

    private SchedulingSpecifications() {
    }

    public static Specification<Scheduling> matching(String search, String taskKey, SchedulingStatus status,
                                                     Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }
            if (taskKey != null && !taskKey.isBlank()) {
                predicates.add(cb.equal(root.get("taskKey"), taskKey.strip()));
            }
            if (status != null) {
                // Same rule as the status shown in responses: PAUSED if disabled, COMPLETED if a one-time
                // scheduling whose run time has passed, otherwise ACTIVE.
                Subquery<OneTimeTiming> pastOneTime = query.subquery(OneTimeTiming.class);
                Root<OneTimeTiming> oneTime = pastOneTime.from(OneTimeTiming.class);
                pastOneTime.select(oneTime).where(
                        cb.equal(oneTime.get("id"), root.get("timing").get("id")),
                        cb.lessThanOrEqualTo(oneTime.get("runAt"), now));
                Predicate enabled = cb.isTrue(root.get("enabled"));
                predicates.add(switch (status) {
                    case PAUSED -> cb.isFalse(root.get("enabled"));
                    case COMPLETED -> cb.and(enabled, cb.exists(pastOneTime));
                    case ACTIVE -> cb.and(enabled, cb.not(cb.exists(pastOneTime)));
                });
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
