package com.github.stimur1709.cloudops.monitoring.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MonitorScheduleIndexIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void actualClaimQueriesUsePartialIndexesWithMostlyInactiveMonitors() {
        jdbc.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings,
                    monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks,
                    organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        long organizationId = jdbc.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Index plan', NOW(), NOW()) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                SELECT 'plan-' || n, 'SERVICE', 'ACTIVE', ?, '{"url":"https://example.com"}'::jsonb, NOW(), NOW()
                FROM GENERATE_SERIES(1, 20000) AS n
                """, organizationId);
        jdbc.execute("""
                INSERT INTO monitors (resource_id, type, compatible, next_run_at, run_requested_at)
                SELECT id, 'HTTP_CHECK', id % 2 = 0,
                    CASE WHEN id <= 20 OR id % 2 = 1 THEN NOW() - INTERVAL '1 minute' END,
                    CASE WHEN id <= 20 THEN NOW() END
                FROM resources
                """);
        jdbc.execute("ANALYZE monitors");
        jdbc.execute("ANALYZE resources");
        String periodicPlan = String.join(
                "\n",
                jdbc.queryForList(
                        "EXPLAIN " + MonitorScheduleRepository.CLAIM_DUE_SQL,
                        String.class,
                        Timestamp.from(Instant.now()),
                        20));
        String requestedPlan = String.join(
                "\n", jdbc.queryForList("EXPLAIN " + MonitorScheduleRepository.CLAIM_REQUESTED_SQL, String.class, 20));
        assertThat(periodicPlan).contains("monitors_due_idx");
        assertThat(requestedPlan).contains("monitors_requested_idx");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM monitors WHERE compatible AND next_run_at IS NOT NULL", Integer.class))
                .isEqualTo(10);
        assertThat(jdbc.queryForList("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'monitors' AND indexname IN ('monitors_due_idx', 'monitors_requested_idx')
                """, String.class))
                .allSatisfy(definition ->
                        assertThat(definition).contains("WHERE", "compatible", "next_run_at IS NOT NULL"));
    }
}
