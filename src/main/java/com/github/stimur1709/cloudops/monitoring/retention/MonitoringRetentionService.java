package com.github.stimur1709.cloudops.monitoring.retention;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.probe.ProbeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringRetentionService {

    private static final String DELETE_EXPIRED_SQL = """
            WITH defaults(probe_type, storage_mode, retention_days) AS (
                VALUES
                    ('HTTP_CHECK', CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('PORT_CHECK', CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('DNS_CHECK', CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('PING', CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('TLS_CHECK', CAST(? AS VARCHAR), CAST(? AS INTEGER))
            ), expired AS (
                SELECT result.id
                FROM monitoring_results result
                JOIN monitors monitor ON monitor.id = result.monitor_id
                JOIN resources resource ON resource.id = monitor.resource_id
                JOIN defaults ON defaults.probe_type = monitor.type
                LEFT JOIN resource_probe_settings resource_settings
                       ON resource_settings.resource_id = monitor.resource_id
                      AND resource_settings.probe_type = monitor.type
                LEFT JOIN organization_probe_settings organization_settings
                       ON organization_settings.organization_id = resource.organization_id
                      AND organization_settings.probe_type = monitor.type
                WHERE COALESCE(resource_settings.storage_mode,
                               organization_settings.storage_mode,
                               defaults.storage_mode) = 'HISTORY'
                  AND result.checked_at < CAST(? AS TIMESTAMPTZ) -
                      CASE
                          WHEN resource_settings.id IS NOT NULL THEN resource_settings.retention_days
                          WHEN organization_settings.id IS NOT NULL THEN organization_settings.retention_days
                          ELSE defaults.retention_days
                      END * INTERVAL '1 day'
                ORDER BY result.checked_at, result.id
                LIMIT ?
                FOR UPDATE OF result SKIP LOCKED
            )
            DELETE FROM monitoring_results result
            USING expired
            WHERE result.id = expired.id
            RETURNING result.id
            """;

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
        var parameters = new ArrayList<>();
        for (ProbeType type : ProbeType.values()) {
            var settings = properties.defaults().get(type);
            parameters.add(settings.storageMode().name());
            parameters.add(settings.retentionDays());
        }
        parameters.add(Timestamp.from(clock.instant()));
        parameters.add(properties.retentionBatchSize());
        return jdbcTemplate.query(DELETE_EXPIRED_SQL, (rs, row) -> rs.getLong("id"), parameters.toArray()).size();
    }
}
