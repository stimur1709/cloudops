package com.github.stimur1709.cloudops.monitoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorClaimService;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionPersistenceService;
import com.github.stimur1709.cloudops.monitoring.retention.MonitoringRetentionService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MonitorApiIntegrationTest {
    @Autowired private WebApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MonitorClaimService claimService;
    @Autowired private MonitorExecutionPersistenceService executionPersistenceService;
    @Autowired private MonitoringRetentionService retentionService;
    private MockMvc mockMvc;
    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_probe_settings, organization_probe_settings, monitoring_results, monitors,
                    resource_health_events, resource_health, outbox_messages, tasks, organization_memberships,
                    resources, users, organizations RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'monitor@example.com', 'Monitor User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Monitoring organization', now(), now()) RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO organization_memberships (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, organizationId, TestAuthentication.USER_ID);
    }

    @Test
    void provisionsCompatibleMonitorsAndReconcilesResourceChanges() throws Exception {
        long resourceId = createService("http://example.com");
        assertThat(types(resourceId)).containsExactlyInAnyOrder("HTTP_CHECK", "DNS_CHECK", "PING");

        updateResource(resourceId, "SERVICE", "{\"url\":\"https://example.com\"}");
        assertThat(types(resourceId)).containsExactlyInAnyOrder("HTTP_CHECK", "DNS_CHECK", "PING", "TLS_CHECK");

        updateResource(resourceId, "OTHER", "{}");
        assertThat(types(resourceId)).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monitors WHERE resource_id = ? AND compatible", Integer.class, resourceId))
                .isZero();
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? ORDER BY id LIMIT 1", Long.class, resourceId);
        assertThat(executionPersistenceService.loadIfExecutable(monitorId)).isNull();
        mockMvc.perform(post("/api/resources/{id}/monitors", resourceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void resolvesWholeSettingsLevelsAndDeletesBackToParent() throws Exception {
        long resourceId = createService("https://example.com");
        mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].source").value("APPLICATION"))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.intervalSeconds").value(30));

        putOrganization("HTTP_CHECK", settings(false, 41, 4, 5, "HISTORY", 21, 901))
                .andExpect(status().isOk());
        assertHttpSettings(resourceId, "ORGANIZATION", false, 41, 4, 5, "HISTORY", 21, 901, false);

        putResource(resourceId, "HTTP_CHECK", settings(true, 52, 6, 7, "LATEST_ONLY", null, 902))
                .andExpect(status().isOk());
        assertHttpSettings(resourceId, "RESOURCE", true, 52, 6, 7, "LATEST_ONLY", null, 902, true);

        mockMvc.perform(delete("/api/resources/{id}/monitoring-settings/HTTP_CHECK", resourceId))
                .andExpect(status().isNoContent());
        assertHttpSettings(resourceId, "ORGANIZATION", false, 41, 4, 5, "HISTORY", 21, 901, false);

        mockMvc.perform(delete("/api/organizations/{id}/monitoring-settings/HTTP_CHECK", organizationId))
                .andExpect(status().isNoContent());
        assertHttpSettings(resourceId, "APPLICATION", true, 30, 3, 2, "LATEST_ONLY", null, 500, false);
    }

    @Test
    void validatesFullTypedSettingsAndEnforcesRbac() throws Exception {
        long resourceId = createService("https://example.com");
        putResource(resourceId, "HTTP_CHECK", "{\"enabled\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'intervalSeconds')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'timeoutMs')]").exists());
        putResource(resourceId, "DNS_CHECK", settings(true, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timeoutMs"));
        putResource(resourceId, "HTTP_CHECK", settings(true, 29, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intervalSeconds"));

        jdbcTemplate.update("UPDATE organization_memberships SET role = 'MEMBER' WHERE organization_id = ?",
                organizationId);
        mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId)).andExpect(status().isOk());
        putResource(resourceId, "HTTP_CHECK", settings(true, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isForbidden());

        long foreignOrganization = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Foreign', now(), now()) RETURNING id
                """, Long.class);
        mockMvc.perform(get("/api/organizations/{id}/monitoring-settings", foreignOrganization))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledProbeKeepsMonitorAndResourceOverrideEnablesSameInstance() throws Exception {
        putOrganization("PING", settings(false, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        long resourceId = createService("http://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'PING'", Long.class, resourceId);
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() - interval '1 second'");
        assertThat(claimService.claimDue()).doesNotContain(monitorId);

        putResource(resourceId, "PING", settings(true, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() - interval '1 second' WHERE id = ?", monitorId);
        assertThat(claimService.claimDue()).contains(monitorId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monitors WHERE resource_id = ? AND type = 'PING'", Integer.class, resourceId))
                .isEqualTo(1);
    }

    @Test
    void deletesOnlyResultsExpiredByEffectiveRetentionSettings() throws Exception {
        long resourceId = createService("https://example.com");
        putResource(resourceId, "HTTP_CHECK", settings(true, 30, 3, 2, "HISTORY", 10, 500))
                .andExpect(status().isOk());
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);
        long expiredId = insertResult(monitorId, "now() - interval '11 days'");
        long retainedId = insertResult(monitorId, "now() - interval '9 days'");

        assertThat(retentionService.deleteExpiredBatch()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT id FROM monitoring_results ORDER BY id", Long.class)).containsExactly(retainedId);
        assertThat(expiredId).isNotEqualTo(retainedId);
    }

    @Test
    void monitorTableContainsOnlyRuntimeState() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'monitors'
                """, String.class);
        assertThat(columns).doesNotContain("enabled", "interval_seconds", "failure_threshold",
                "recovery_threshold", "storage_mode", "retention_days");
    }

    private long createService(String url) throws Exception {
        String response = mockMvc.perform(post("/api/resources").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"service-%d","type":"SERVICE","status":"ACTIVE","organizationId":%d,
                 "config":{"url":"%s"}}
                """.formatted(System.nanoTime(), organizationId, url)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void updateResource(long resourceId, String type, String config) throws Exception {
        mockMvc.perform(put("/api/resources/{id}", resourceId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"updated","type":"%s","status":"ACTIVE","organizationId":%d,"config":%s}
                """.formatted(type, organizationId, config))).andExpect(status().isOk());
    }

    private List<String> types(long resourceId) {
        return jdbcTemplate.queryForList("SELECT type FROM monitors WHERE resource_id = ?", String.class, resourceId);
    }

    private long insertResult(long monitorId, String checkedAtExpression) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO monitoring_results (monitor_id, checked_at, result)
                VALUES (?, %s, '{}'::jsonb)
                RETURNING id
                """.formatted(checkedAtExpression), Long.class, monitorId);
    }

    private org.springframework.test.web.servlet.ResultActions putOrganization(String type, String body) throws Exception {
        return mockMvc.perform(put("/api/organizations/{id}/monitoring-settings/{type}", organizationId, type)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions putResource(long resourceId, String type, String body)
            throws Exception {
        return mockMvc.perform(put("/api/resources/{id}/monitoring-settings/{type}", resourceId, type)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String settings(boolean enabled, int interval, int failure, int recovery,
            String storage, Integer retention, Integer timeout) {
        return """
                {"enabled":%s,"intervalSeconds":%d,"failureThreshold":%d,"recoveryThreshold":%d,
                 "storageMode":"%s","retentionDays":%s,"timeoutMs":%s}
                """.formatted(enabled, interval, failure, recovery, storage,
                retention == null ? "null" : retention, timeout == null ? "null" : timeout);
    }

    private void assertHttpSettings(long resourceId, String source, boolean enabled, int interval,
            int failure, int recovery, String storage, Integer retention, int timeout, boolean override)
            throws Exception {
        var result = mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].source").value(source))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.enabled").value(enabled))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.intervalSeconds").value(interval))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.failureThreshold").value(failure))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.recoveryThreshold").value(recovery))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.storageMode").value(storage))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.timeoutMs").value(timeout))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].resourceOverride").value(override));
        if (retention == null) result.andExpect(jsonPath(
                "$[?(@.probeType == 'HTTP_CHECK')].effective.retentionDays").value(
                        org.hamcrest.Matchers.contains((Object) null)));
        else result.andExpect(jsonPath(
                "$[?(@.probeType == 'HTTP_CHECK')].effective.retentionDays").value(retention));
    }
}
