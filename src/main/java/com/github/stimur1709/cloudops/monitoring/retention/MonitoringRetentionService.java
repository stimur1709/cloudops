package com.github.stimur1709.cloudops.monitoring.retention;

import java.sql.Timestamp;
import java.time.Clock;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringRetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitoringRetentionService(JdbcTemplate jdbcTemplate, MonitoringProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int deleteExpiredBatch() {
        return jdbcTemplate.update("""
                WITH expired AS (
                    SELECT result.id
                    FROM monitoring_results result
                    JOIN monitors monitor ON monitor.id = result.monitor_id
                    WHERE monitor.storage_mode = 'HISTORY'
                      AND result.checked_at < ? - monitor.retention_days * INTERVAL '1 day'
                    ORDER BY result.checked_at, result.id
                    LIMIT ?
                    FOR UPDATE OF result SKIP LOCKED
                )
                DELETE FROM monitoring_results result
                USING expired
                WHERE result.id = expired.id
                """, Timestamp.from(clock.instant()), properties.retentionBatchSize());
    }
}
