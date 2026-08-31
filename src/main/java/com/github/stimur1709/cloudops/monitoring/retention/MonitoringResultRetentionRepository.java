package com.github.stimur1709.cloudops.monitoring.retention;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitoringResultRetentionRepository {

    private static final String DELETE_EXPIRED_SQL = """
            WITH application_settings(probe_type, storage_mode, retention_days) AS (
                VALUES %s
            ), effective_retention AS (
                SELECT monitor.id AS monitor_id,
                       COALESCE(resource_settings.storage_mode,
                                organization_settings.storage_mode,
                                application_settings.storage_mode) AS storage_mode,
                       COALESCE(resource_settings.retention_days,
                                organization_settings.retention_days,
                                application_settings.retention_days) AS retention_days
                FROM monitors AS monitor
                JOIN resources AS resource
                  ON resource.id = monitor.resource_id
                JOIN application_settings
                  ON application_settings.probe_type = monitor.type
                LEFT JOIN resource_probe_settings AS resource_settings
                  ON resource_settings.resource_id = monitor.resource_id
                 AND resource_settings.probe_type = monitor.type
                LEFT JOIN organization_probe_settings AS organization_settings
                  ON organization_settings.organization_id = resource.organization_id
                 AND organization_settings.probe_type = monitor.type
            ), expired AS (
                SELECT result.id
                FROM monitoring_results AS result
                JOIN effective_retention AS retention
                  ON retention.monitor_id = result.monitor_id
                WHERE retention.storage_mode = 'HISTORY'
                  AND result.checked_at < CAST(? AS TIMESTAMPTZ)
                                          - retention.retention_days * INTERVAL '1 day'
                ORDER BY result.checked_at, result.id
                LIMIT ?
                FOR UPDATE OF result SKIP LOCKED
            )
            DELETE FROM monitoring_results AS result
            USING expired
            WHERE result.id = expired.id
            RETURNING result.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MonitoringProperties properties;

    public MonitoringResultRetentionRepository(JdbcTemplate jdbcTemplate, MonitoringProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public int deleteExpired(Instant now, int batchSize) {
        List<Object> parameters = applicationDefaultParameters();
        parameters.add(Timestamp.from(now));
        parameters.add(batchSize);
        String values = String.join(
                ", ",
                Collections.nCopies(
                        ProbeType.values().length, "(CAST(? AS VARCHAR), CAST(? AS VARCHAR), CAST(? AS INTEGER))"));
        return jdbcTemplate
                .query(
                        DELETE_EXPIRED_SQL.formatted(values),
                        (resultSet, _) -> resultSet.getLong("id"),
                        parameters.toArray())
                .size();
    }

    List<Object> applicationDefaultParameters() {
        List<Object> parameters = new ArrayList<>();
        for (ProbeType type : ProbeType.values()) {
            var settings = properties.defaults().get(type);
            parameters.add(type.name());
            parameters.add(settings.storageMode().name());
            parameters.add(settings.retentionDays());
        }
        return parameters;
    }
}
