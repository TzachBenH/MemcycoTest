package com.memcyco.scheduler.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Edit locks with plain SQL: acquire / take-over-if-expired / renew must each be one atomic statement, and they
 * use the <b>database clock</b> ({@code now()}) so several backend instances agree on expiry.
 * A lock is "active" while {@code expires_at >= now()}.
 */
@Repository
public class EditLockRepository {

    private static final String COLUMNS = "scheduling_id, lock_token, locked_by, acquired_at, expires_at";
    private static final RowMapper<EditLock> ROW_MAPPER = EditLockRepository::map;

    private final NamedParameterJdbcTemplate jdbc;

    public EditLockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Takes the lock if it is free, expired, or already held by the same owner (same browser tab reopening).
     * Two concurrent attempts cannot both succeed.
     *
     * @return the new lock, or empty if someone else holds an active lock
     */
    public Optional<EditLock> tryAcquire(UUID schedulingId, UUID token, String owner, Duration ttl) {
        String sql = """
                INSERT INTO scheduling_edit_locks AS l (%s)
                VALUES (:id, :token, :owner, now(), now() + :ttl * interval '1 second')
                ON CONFLICT (scheduling_id) DO UPDATE
                   SET lock_token = EXCLUDED.lock_token, locked_by = EXCLUDED.locked_by,
                       acquired_at = EXCLUDED.acquired_at, expires_at = EXCLUDED.expires_at
                 WHERE l.expires_at < now() OR l.locked_by = EXCLUDED.locked_by
                RETURNING %s
                """.formatted(COLUMNS, COLUMNS);
        return single(jdbc.query(sql, params(schedulingId, token).addValue("owner", owner)
                .addValue("ttl", ttl.toSeconds()), ROW_MAPPER));
    }

    /** Extends an active lock held with this token; empty if it expired or was taken over. */
    public Optional<EditLock> renew(UUID schedulingId, UUID token, Duration ttl) {
        String sql = """
                UPDATE scheduling_edit_locks
                   SET expires_at = now() + :ttl * interval '1 second'
                 WHERE scheduling_id = :id AND lock_token = :token AND expires_at >= now()
                RETURNING %s
                """.formatted(COLUMNS);
        return single(jdbc.query(sql, params(schedulingId, token).addValue("ttl", ttl.toSeconds()), ROW_MAPPER));
    }

    public void release(UUID schedulingId, UUID token) {
        jdbc.update("DELETE FROM scheduling_edit_locks WHERE scheduling_id = :id AND lock_token = :token",
                params(schedulingId, token));
    }

    /** Removes locks that expired (housekeeping; expired locks are already ignored everywhere). */
    public int deleteExpired() {
        return jdbc.update("DELETE FROM scheduling_edit_locks WHERE expires_at < now()", new MapSqlParameterSource());
    }

    public boolean isHeld(UUID schedulingId, UUID token) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM scheduling_edit_locks
                 WHERE scheduling_id = :id AND lock_token = :token AND expires_at >= now()
                """, params(schedulingId, token), Integer.class);
        return count != null && count > 0;
    }

    public Optional<EditLock> findActive(UUID schedulingId) {
        return findActive(List.of(schedulingId)).values().stream().findFirst();
    }

    public Map<UUID, EditLock> findActive(Collection<UUID> schedulingIds) {
        if (schedulingIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT " + COLUMNS + " FROM scheduling_edit_locks "
                + "WHERE scheduling_id IN (:ids) AND expires_at >= now()";
        return jdbc.query(sql, new MapSqlParameterSource("ids", schedulingIds), ROW_MAPPER).stream()
                .collect(Collectors.toMap(EditLock::schedulingId, Function.identity()));
    }

    private static MapSqlParameterSource params(UUID schedulingId, UUID token) {
        return new MapSqlParameterSource("id", schedulingId).addValue("token", token);
    }

    private static Optional<EditLock> single(List<EditLock> rows) {
        return rows.stream().findFirst();
    }

    private static EditLock map(ResultSet rs, int rowNum) throws SQLException {
        return new EditLock(rs.getObject("scheduling_id", UUID.class), rs.getObject("lock_token", UUID.class),
                rs.getString("locked_by"), instant(rs, "acquired_at"), instant(rs, "expires_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
