package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorScheduleRepository {

    private static final String CLAIM_DUE_SQL = """
            SELECT monitor.id, monitor.resource_id, resource.organization_id, monitor.type
            FROM monitors AS monitor
            JOIN resources AS resource ON resource.id = monitor.resource_id
            WHERE monitor.next_run_at IS NOT NULL
              AND monitor.next_run_at <= CAST(? AS TIMESTAMPTZ)
              AND monitor.compatible
            ORDER BY monitor.next_run_at, monitor.id
            LIMIT ?
            FOR UPDATE OF monitor SKIP LOCKED
            """;

    private static final String SCHEDULE_NEXT_SQL = """
            UPDATE monitors
            SET next_run_at = CAST(? AS TIMESTAMPTZ),
                requested_run_at = NULL
            WHERE id = ?
            """;

    private static final String CLAIM_REQUESTED_SQL = """
            SELECT monitor.id
            FROM monitors AS monitor
            WHERE monitor.requested_run_at IS NOT NULL
              AND monitor.compatible
            ORDER BY monitor.requested_run_at, monitor.id
            LIMIT ?
            FOR UPDATE OF monitor SKIP LOCKED
            """;

    private static final String CLEAR_REQUEST_SQL = """
            UPDATE monitors
            SET requested_run_at = NULL
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public MonitorScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ClaimedMonitor> claimDue(Instant now, int batchSize) {
        return jdbcTemplate.query(
                CLAIM_DUE_SQL,
                (resultSet, _) -> new ClaimedMonitor(
                        resultSet.getLong("id"),
                        resultSet.getLong("resource_id"),
                        resultSet.getLong("organization_id"),
                        ProbeType.valueOf(resultSet.getString("type"))),
                Timestamp.from(now),
                batchSize);
    }

    public void scheduleNext(long monitorId, Instant nextRunAt) {
        jdbcTemplate.update(SCHEDULE_NEXT_SQL, Timestamp.from(nextRunAt), monitorId);
    }

    public List<Long> claimRequested(int batchSize) {
        List<Long> claimed =
                jdbcTemplate.query(CLAIM_REQUESTED_SQL, (resultSet, _) -> resultSet.getLong("id"), batchSize);
        claimed.forEach(id -> jdbcTemplate.update(CLEAR_REQUEST_SQL, id));
        return claimed;
    }

    public record ClaimedMonitor(long id, long resourceId, long organizationId, ProbeType type) {}
}
