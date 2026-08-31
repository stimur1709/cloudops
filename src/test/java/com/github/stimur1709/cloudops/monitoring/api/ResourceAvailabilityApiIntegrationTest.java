package com.github.stimur1709.cloudops.monitoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.github.stimur1709.cloudops.SqlStatementRecorder;
import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResourceAvailabilityApiIntegrationTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SqlStatementRecorder sqlStatementRecorder;

    private MockMvc mockMvc;
    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health,
                    outbox_messages, tasks, organization_memberships, resources, users, organizations
                    RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'availability@example.com', 'Availability User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        organizationId = insertOrganization("Availability organization");
        jdbcTemplate.update("""
                INSERT INTO organization_memberships (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, organizationId, TestAuthentication.USER_ID);
    }

    @ParameterizedTest
    @EnumSource(ResourceType.class)
    void returnsUnknownForEveryResourceTypeWithoutHealthHistory(ResourceType resourceType) throws Exception {
        long resourceId = insertResource(organizationId, "unknown-" + resourceType, resourceType);

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T10:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-28T10:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-08-28T11:00:00Z"))
                .andExpect(jsonPath("$.periodSeconds").value(3600))
                .andExpect(jsonPath("$.upSeconds").value(0))
                .andExpect(jsonPath("$.degradedSeconds").value(0))
                .andExpect(jsonPath("$.downSeconds").value(0))
                .andExpect(jsonPath("$.unknownSeconds").value(3600))
                .andExpect(jsonPath("$.knownSeconds").value(0))
                .andExpect(jsonPath("$.uptimePercent").doesNotExist())
                .andExpect(jsonPath("$.availabilityPercent").doesNotExist())
                .andExpect(jsonPath("$.coveragePercent").value(0.00));
    }

    @Test
    void usesLastEventAtFromAndExcludesEventAtTo() throws Exception {
        long resourceId = insertResource(organizationId, "boundary-resource");
        long otherResourceId = insertResource(organizationId, "other-resource");
        insertEvent(resourceId, "UNKNOWN", "DOWN", "2026-08-28T09:40:00Z");
        insertEvent(resourceId, "DOWN", "UP", "2026-08-28T10:00:00Z");
        insertEvent(resourceId, "UP", "DEGRADED", "2026-08-28T10:20:00Z");
        insertEvent(resourceId, "DEGRADED", "DOWN", "2026-08-28T10:30:00Z");
        insertEvent(resourceId, "DOWN", "UP", "2026-08-28T10:45:00Z");
        insertEvent(resourceId, "UP", "DOWN", "2026-08-28T11:00:00Z");
        insertEvent(otherResourceId, "UNKNOWN", "DOWN", "2026-08-28T10:05:00Z");
        sqlStatementRecorder.clear();

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T10:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodSeconds").value(3600))
                .andExpect(jsonPath("$.upSeconds").value(2100))
                .andExpect(jsonPath("$.degradedSeconds").value(600))
                .andExpect(jsonPath("$.downSeconds").value(900))
                .andExpect(jsonPath("$.unknownSeconds").value(0))
                .andExpect(jsonPath("$.knownSeconds").value(3600))
                .andExpect(jsonPath("$.uptimePercent").value(58.33))
                .andExpect(jsonPath("$.availabilityPercent").value(75.00))
                .andExpect(jsonPath("$.coveragePercent").value(100.00));

        List<String> eventQueries = sqlStatementRecorder.statements().stream()
                .map(String::toLowerCase)
                .filter(statement -> statement.contains(" from resource_health_events "))
                .toList();
        assertThat(eventQueries).hasSize(2).allMatch(statement -> statement.contains("resource_id"));
        assertThat(sqlStatementRecorder.statements().stream()
                .map(String::toLowerCase)
                .noneMatch(statement -> statement.contains(" from monitoring_results "))).isTrue();
    }

    @Test
    void usesLastEventBeforeFromAsInitialState() throws Exception {
        long resourceId = insertResource(organizationId, "initial-state-resource");
        insertEvent(resourceId, "UNKNOWN", "UP", "2026-08-28T09:50:00Z");

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T10:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upSeconds").value(3600))
                .andExpect(jsonPath("$.knownSeconds").value(3600))
                .andExpect(jsonPath("$.uptimePercent").value(100.00))
                .andExpect(jsonPath("$.availabilityPercent").value(100.00))
                .andExpect(jsonPath("$.coveragePercent").value(100.00));
    }

    @Test
    void validatesPeriodAndRequiredParameters() throws Exception {
        long resourceId = insertResource(organizationId, "validation-resource");

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T11:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].message").value("To must be after from"));

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T12:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));

        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    @Test
    void returnsNotFoundForMissingAndForeignResources() throws Exception {
        long foreignOrganizationId = insertOrganization("Foreign organization");
        long foreignResourceId = insertResource(foreignOrganizationId, "foreign-resource");

        assertNotFound(foreignResourceId);
        assertNotFound(999_999L);
    }

    private void assertNotFound(long resourceId) throws Exception {
        mockMvc.perform(get("/api/resources/{id}/health/availability", resourceId)
                        .queryParam("from", "2026-08-28T10:00:00Z")
                        .queryParam("to", "2026-08-28T11:00:00Z"))
                .andExpect(status().isNotFound());
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES (?, now(), now()) RETURNING id
                """, Long.class, name);
    }

    private long insertResource(long targetOrganizationId, String name) {
        return insertResource(targetOrganizationId, name, ResourceType.SERVICE);
    }

    private long insertResource(long targetOrganizationId, String name, ResourceType type) {
        long resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', ?, '{}'::jsonb, now(), now()) RETURNING id
                """, Long.class, name, type.name(), targetOrganizationId);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UNKNOWN')",
                resourceId
        );
        return resourceId;
    }

    private void insertEvent(long resourceId, String from, String to, String changedAt) {
        jdbcTemplate.update("""
                INSERT INTO resource_health_events (resource_id, from_status, to_status, changed_at)
                VALUES (?, ?, ?, CAST(? AS TIMESTAMP WITH TIME ZONE))
                """, resourceId, from, to, changedAt);
    }
}
