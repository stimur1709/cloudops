package com.github.stimur1709.cloudops.monitoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionPersistenceService;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResourceHealthAtomicityIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MonitorExecutionPersistenceService persistenceService;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ResourceHealthEventJpaRepository eventRepository;

    private long monitorId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health,
                    outbox_messages, tasks, organization_memberships, resources, users, organizations
                    RESTART IDENTITY
                """);
        long organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Atomic health', now(), now()) RETURNING id
                """, Long.class);
        resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('api', 'SERVICE', 'ACTIVE', ?, '{"url":"https://example.com"}'::jsonb, now(), now()) RETURNING id
                """, Long.class, organizationId);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UNKNOWN')",
                resourceId
        );
        monitorId = jdbcTemplate.queryForObject("""
                INSERT INTO monitors
                    (resource_id, type, next_run_at)
                VALUES (?, 'HTTP_CHECK', now()) RETURNING id
                """, Long.class, resourceId);
    }

    @Test
    void rollsBackMonitorAndCurrentResourceHealthWhenEventCannotBeSaved() {
        when(eventRepository.save(any(ResourceHealthEventEntity.class)))
                .thenThrow(new IllegalStateException("event persistence failed"));

        assertThatThrownBy(() -> persistenceService.saveResult(
                monitorId, Instant.now(), objectMapper.createObjectNode(), true
        )).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT health_status FROM resource_health WHERE resource_id = ?",
                String.class,
                resourceId
        )).isEqualTo("UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT health_status FROM monitors WHERE id = ?",
                String.class,
                monitorId
        )).isEqualTo("UNKNOWN");
    }
}
