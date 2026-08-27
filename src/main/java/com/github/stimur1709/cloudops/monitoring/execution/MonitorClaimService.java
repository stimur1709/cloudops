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
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                FROM monitors
                WHERE enabled = true AND next_run_at <= ?
                ORDER BY next_run_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, Long.class, Timestamp.from(now), properties.batchSize());
        for (long id : ids) {
            jdbcTemplate.update("""
                    UPDATE monitors
                    SET next_run_at = ? + interval_seconds * INTERVAL '1 second'
                    WHERE id = ?
                    """, Timestamp.from(now), id);
        }
        return ids;
    }
}
