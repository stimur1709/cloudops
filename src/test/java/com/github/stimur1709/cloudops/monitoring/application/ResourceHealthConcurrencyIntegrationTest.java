package com.github.stimur1709.cloudops.monitoring.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionPersistenceService;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResourceHealthConcurrencyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MonitorExecutionPersistenceService persistenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks,
                    organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
    }

    @Test
    void concurrentMonitorCompletionsLeaveFullyRecalculatedHealth() throws Exception {
        long organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Concurrent monitoring', now(), now()) RETURNING id
                """, Long.class);
        long resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('api', 'SERVICE', 'ACTIVE', ?, '{"url":"https://example.com"}'::jsonb, now(), now()) RETURNING id
                """, Long.class, organizationId);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UNKNOWN')", resourceId);

        jdbcTemplate.execute("ALTER TABLE monitors DROP CONSTRAINT monitors_resource_type_key");
        try {
            long upMonitorId = insertMonitor(resourceId);
            long downMonitorId = insertMonitor(resourceId);
            CountDownLatch start = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> up = executor.submit(() -> {
                    await(start);
                    persistenceService.saveResult(upMonitorId, Instant.now(), objectMapper.createObjectNode(), true);
                });
                Future<?> down = executor.submit(() -> {
                    await(start);
                    persistenceService.saveResult(downMonitorId, Instant.now(), objectMapper.createObjectNode(), false);
                });
                start.countDown();
                up.get();
                down.get();
            }

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT health_status FROM resource_health WHERE resource_id = ?",
                            String.class,
                            resourceId))
                    .isEqualTo("DEGRADED");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM resource_health_events WHERE resource_id = ?",
                            Integer.class,
                            resourceId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT from_status || '->' || to_status FROM resource_health_events
                    WHERE resource_id = ?
                    """, String.class, resourceId))
                    .isEqualTo("UNKNOWN->DEGRADED");
        } finally {
            jdbcTemplate.update("DELETE FROM monitors WHERE resource_id = ?", resourceId);
            jdbcTemplate.execute("""
                    ALTER TABLE monitors ADD CONSTRAINT monitors_resource_type_key UNIQUE (resource_id, type)
                    """);
        }
    }

    private long insertMonitor(long resourceId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO monitors
                    (resource_id, type, next_run_at)
                VALUES (?, 'HTTP_CHECK', now()) RETURNING id
                """, Long.class, resourceId);
    }

    private void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
