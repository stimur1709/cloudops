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
            SELECT id, resource_id, type
            FROM monitors
            WHERE next_run_at <= CAST(? AS TIMESTAMPTZ)
              AND compatible
            ORDER BY next_run_at, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String SCHEDULE_NEXT_SQL = """
            UPDATE monitors
            SET next_run_at = CAST(? AS TIMESTAMPTZ)
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
                        ProbeType.valueOf(resultSet.getString("type"))),
                Timestamp.from(now),
                batchSize);
    }

    public void scheduleNext(long monitorId, Instant nextRunAt) {
        jdbcTemplate.update(SCHEDULE_NEXT_SQL, Timestamp.from(nextRunAt), monitorId);
    }

    public record ClaimedMonitor(long id, long resourceId, ProbeType type) {}
}
