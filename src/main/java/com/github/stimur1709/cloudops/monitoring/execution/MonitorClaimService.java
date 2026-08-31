package com.github.stimur1709.cloudops.monitoring.execution;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.probe.ProbeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorClaimService {

    private static final String CLAIM_DUE_SQL = """
            WITH defaults(probe_type, enabled, interval_seconds) AS (
                VALUES
                    ('HTTP_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER)),
                    ('PORT_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER)),
                    ('DNS_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER)),
                    ('PING', CAST(? AS BOOLEAN), CAST(? AS INTEGER)),
                    ('TLS_CHECK', CAST(? AS BOOLEAN), CAST(? AS INTEGER))
            ), due AS (
                SELECT monitor.id,
                       COALESCE(resource_settings.interval_seconds,
                                organization_settings.interval_seconds,
                                defaults.interval_seconds) AS interval_seconds
                FROM monitors monitor
                JOIN resources resource ON resource.id = monitor.resource_id
                JOIN defaults ON defaults.probe_type = monitor.type
                LEFT JOIN resource_probe_settings resource_settings
                       ON resource_settings.resource_id = monitor.resource_id
                      AND resource_settings.probe_type = monitor.type
                LEFT JOIN organization_probe_settings organization_settings
                       ON organization_settings.organization_id = resource.organization_id
                      AND organization_settings.probe_type = monitor.type
                WHERE monitor.next_run_at <= CAST(? AS TIMESTAMPTZ)
                  AND monitor.compatible
                  AND COALESCE(resource_settings.enabled,
                               organization_settings.enabled,
                               defaults.enabled)
                ORDER BY monitor.next_run_at, monitor.id
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
        Timestamp now = Timestamp.from(clock.instant());
        var parameters = new ArrayList<>();
        for (ProbeType type : ProbeType.values()) {
            var settings = properties.defaults().get(type);
            parameters.add(settings.enabled());
            parameters.add(settings.intervalSeconds());
        }
        parameters.add(now);
        parameters.add(properties.batchSize());
        parameters.add(now);
        return jdbcTemplate.query(CLAIM_DUE_SQL, (rs, row) -> rs.getLong("id"), parameters.toArray());
    }
}
