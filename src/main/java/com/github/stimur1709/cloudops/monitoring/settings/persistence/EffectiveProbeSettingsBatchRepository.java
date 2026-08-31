package com.github.stimur1709.cloudops.monitoring.settings.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.probe.ProbeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EffectiveProbeSettingsBatchRepository {

    private static final String EFFECTIVE_SETTINGS_CTE = """
            WITH application_settings(probe_type, enabled, interval_seconds, storage_mode, retention_days) AS (
                VALUES
                    ('HTTP_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER), CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('PORT_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER), CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('DNS_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER), CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('PING', CAST(? AS BOOLEAN), CAST(? AS INTEGER), CAST(? AS VARCHAR), CAST(? AS INTEGER)),
                    ('TLS_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER), CAST(? AS VARCHAR), CAST(? AS INTEGER))
            ), effective_settings AS (
                SELECT monitor.id AS monitor_id,
                       monitor.next_run_at,
                       monitor.compatible,
                       CASE
                           WHEN resource_settings.id IS NOT NULL THEN resource_settings.enabled
                           WHEN organization_settings.id IS NOT NULL THEN organization_settings.enabled
                           ELSE application_settings.enabled
                       END AS enabled,
                       CASE
                           WHEN resource_settings.id IS NOT NULL THEN resource_settings.interval_seconds
                           WHEN organization_settings.id IS NOT NULL THEN organization_settings.interval_seconds
                           ELSE application_settings.interval_seconds
                       END AS interval_seconds,
                       CASE
                           WHEN resource_settings.id IS NOT NULL THEN resource_settings.storage_mode
                           WHEN organization_settings.id IS NOT NULL THEN organization_settings.storage_mode
                           ELSE application_settings.storage_mode
                       END AS storage_mode,
                       CASE
                           WHEN resource_settings.id IS NOT NULL THEN resource_settings.retention_days
                           WHEN organization_settings.id IS NOT NULL THEN organization_settings.retention_days
                           ELSE application_settings.retention_days
                       END AS retention_days
                FROM monitors monitor
                JOIN resources resource ON resource.id = monitor.resource_id
                JOIN application_settings ON application_settings.probe_type = monitor.type
                LEFT JOIN resource_probe_settings resource_settings
                       ON resource_settings.resource_id = monitor.resource_id
                      AND resource_settings.probe_type = monitor.type
                LEFT JOIN organization_probe_settings organization_settings
                       ON organization_settings.organization_id = resource.organization_id
                      AND organization_settings.probe_type = monitor.type
            )
            """;

    private static final String CLAIM_DUE_SQL = EFFECTIVE_SETTINGS_CTE + """
            , due AS (
                SELECT monitor.id, settings.interval_seconds
                FROM effective_settings settings
                JOIN monitors monitor ON monitor.id = settings.monitor_id
                WHERE settings.next_run_at <= CAST(? AS TIMESTAMPTZ)
                  AND settings.compatible
                  AND settings.enabled
                ORDER BY settings.next_run_at, monitor.id
                LIMIT ?
                FOR UPDATE OF monitor SKIP LOCKED
            )
            UPDATE monitors monitor
            SET next_run_at = CAST(? AS TIMESTAMPTZ)
                              + due.interval_seconds * INTERVAL '1 second'
            FROM due
            WHERE monitor.id = due.id
            RETURNING monitor.id
            """;

    private static final String DELETE_EXPIRED_SQL = EFFECTIVE_SETTINGS_CTE + """
            , expired AS (
                SELECT result.id
                FROM monitoring_results result
                JOIN effective_settings settings ON settings.monitor_id = result.monitor_id
                WHERE settings.storage_mode = 'HISTORY'
                  AND result.checked_at < CAST(? AS TIMESTAMPTZ)
                                                - settings.retention_days * INTERVAL '1 day'
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

    public EffectiveProbeSettingsBatchRepository(JdbcTemplate jdbcTemplate, MonitoringProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<Long> claimDue(Instant now, int batchSize) {
        List<Object> parameters = defaultParameters();
        parameters.add(Timestamp.from(now));
        parameters.add(batchSize);
        parameters.add(Timestamp.from(now));
        return selectIds(CLAIM_DUE_SQL, parameters);
    }

    public int deleteExpired(Instant now, int batchSize) {
        List<Object> parameters = defaultParameters();
        parameters.add(Timestamp.from(now));
        parameters.add(batchSize);
        return selectIds(DELETE_EXPIRED_SQL, parameters).size();
    }

    private List<Object> defaultParameters() {
        List<Object> parameters = new ArrayList<>();
        for (ProbeType type : ProbeType.values()) {
            var settings = properties.defaults().get(type);
            parameters.add(settings.enabled());
            parameters.add(settings.intervalSeconds());
            parameters.add(settings.storageMode().name());
            parameters.add(settings.retentionDays());
        }
        return parameters;
    }

    private List<Long> selectIds(String sql, List<Object> parameters) {
        return jdbcTemplate.query(sql, (resultSet, _) -> resultSet.getLong("id"), parameters.toArray());
    }
}
