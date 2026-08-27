package com.github.stimur1709.cloudops.monitoring.execution;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorClaimService {

    private final JdbcTemplate jdbcTemplate;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitorClaimService(JdbcTemplate jdbcTemplate, MonitoringProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<Long> claimDue() {
        Instant now = clock.instant();
        Timestamp claimedAt = Timestamp.from(now);
        return jdbcTemplate.queryForList("""
                WITH due AS (
                    SELECT id
                    FROM monitors
                    WHERE enabled = true AND next_run_at <= ?
                    ORDER BY next_run_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE monitors monitor
                SET next_run_at = CAST(? AS TIMESTAMPTZ)
                        + monitor.interval_seconds * INTERVAL '1 second'
                FROM due
                WHERE monitor.id = due.id
                RETURNING monitor.id
                """, Long.class, claimedAt, properties.batchSize(), claimedAt);
    }
}
