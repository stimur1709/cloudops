package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity_;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity_;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorScheduleRepository {

    static final String CLAIM_DUE_SQL = """
        SELECT monitor.id,
               monitor.resource_id AS "resourceId",
               resource.organization_id AS "organizationId",
               monitor.type,
               monitor.next_run_at AS "nextRunAt"
        FROM monitors AS monitor
        JOIN resources AS resource ON resource.id = monitor.resource_id
        WHERE monitor.next_run_at IS NOT NULL
          AND monitor.next_run_at <= CAST(? AS TIMESTAMPTZ)
          AND monitor.compatible
        ORDER BY monitor.next_run_at, monitor.id
        LIMIT ?
        FOR UPDATE OF monitor SKIP LOCKED
        """;

    static final String CLAIM_REQUESTED_SQL = """
        SELECT monitor.id,
               monitor.resource_id AS "resourceId",
               resource.organization_id AS "organizationId",
               monitor.type,
               monitor.next_run_at AS "nextRunAt"
        FROM monitors AS monitor
        JOIN resources AS resource ON resource.id = monitor.resource_id
        WHERE monitor.run_requested_at IS NOT NULL
          AND monitor.next_run_at IS NOT NULL
          AND monitor.compatible
        ORDER BY monitor.run_requested_at, monitor.id
        LIMIT ?
        FOR UPDATE OF monitor SKIP LOCKED
        """;

    private static final String SCHEDULE_NEXT_SQL = """
        UPDATE monitors
        SET next_run_at = CAST(? AS TIMESTAMPTZ), run_requested_at = NULL
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
                        resultSet.getLong(MonitorEntity_.ID),
                        resultSet.getLong(MonitorEntity_.RESOURCE_ID),
                        resultSet.getLong(ResourceEntity_.ORGANIZATION_ID),
                        ProbeType.valueOf(resultSet.getString(MonitorEntity_.TYPE)),
                        resultSet.getTimestamp(MonitorEntity_.NEXT_RUN_AT).toInstant()),
                Timestamp.from(now),
                batchSize);
    }

    public List<ClaimedMonitor> claimRequested(int batchSize) {
        return jdbcTemplate.query(
                CLAIM_REQUESTED_SQL,
                (resultSet, _) -> new ClaimedMonitor(
                        resultSet.getLong(MonitorEntity_.ID),
                        resultSet.getLong(MonitorEntity_.RESOURCE_ID),
                        resultSet.getLong(ResourceEntity_.ORGANIZATION_ID),
                        ProbeType.valueOf(resultSet.getString(MonitorEntity_.TYPE)),
                        resultSet.getTimestamp(MonitorEntity_.NEXT_RUN_AT).toInstant()),
                batchSize);
    }

    public void scheduleNext(long monitorId, Instant nextRunAt) {
        jdbcTemplate.update(SCHEDULE_NEXT_SQL, Timestamp.from(nextRunAt), monitorId);
    }
}
