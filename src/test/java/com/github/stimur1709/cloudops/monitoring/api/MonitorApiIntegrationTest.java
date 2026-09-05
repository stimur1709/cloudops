package com.github.stimur1709.cloudops.monitoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorClaimService;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionPersistenceService;
import com.github.stimur1709.cloudops.monitoring.retention.MonitoringRetentionService;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsIndex;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MonitorClaimService claimService;

    @Autowired
    private MonitorExecutionPersistenceService executionPersistenceService;

    @Autowired
    private MonitoringRetentionService retentionService;

    @Autowired
    private MonitoringSettingsIndex settingsIndex;

    private MockMvc mockMvc;
    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors,
                    resource_health_events, resource_health, outbox_messages, tasks, organization_memberships,
                    resources, users, organizations RESTART IDENTITY
                """);
        settingsIndex.reload();
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
                        "SELECT count(*) FROM monitors WHERE resource_id = ? AND compatible",
                        Integer.class,
                        resourceId))
                .isZero();
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? ORDER BY id LIMIT 1", Long.class, resourceId);
        assertThat(executionPersistenceService.loadIfExecutable(monitorId)).isNull();
        assertThat(nextRunAt(monitorId)).isNull();

        updateResource(resourceId, "SERVICE", "{\"url\":\"https://example.com\"}");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT compatible FROM monitors WHERE id = ?", Boolean.class, monitorId))
                .isTrue();
        assertThat(nextRunAt(monitorId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM monitors WHERE resource_id = ?", Integer.class, resourceId))
                .isEqualTo(4);

        mockMvc.perform(post("/api/resources/{id}/monitors", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void provisionsOneSshMonitorForCompatibleResourcesAndExposesEffectiveSettings() throws Exception {
        String response = mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"name":"ssh-server","type":"SERVER","status":"ACTIVE","organizationId":%d,
                 "config":{"host":"server.internal"}}
                """.formatted(organizationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.sshPort").value(22))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long resourceId = ((Number) JsonPath.read(response, "$.id")).longValue();

        assertThat(types(resourceId)).contains("SSH_CHECK");
        mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.probeType == 'SSH_CHECK')].supported").value(true))
                .andExpect(jsonPath("$[?(@.probeType == 'SSH_CHECK')].effective.timeoutMs")
                        .value(500));

        updateResource(resourceId, "SERVER", "{\"host\":\"updated.internal\",\"sshPort\":2222}");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM monitors WHERE resource_id = ? AND type = 'SSH_CHECK'",
                        Integer.class,
                        resourceId))
                .isEqualTo(1);

        updateResource(resourceId, "OTHER", "{}");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT compatible FROM monitors WHERE resource_id = ? AND type = 'SSH_CHECK'",
                        Boolean.class,
                        resourceId))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UP", "DOWN"})
    void newUnknownMonitorKeepsKnownResourceHealthWithoutCreatingEvent(String knownStatus) throws Exception {
        long resourceId = createService("http://example.com");
        jdbcTemplate.update("UPDATE monitors SET health_status = ? WHERE resource_id = ?", knownStatus, resourceId);
        jdbcTemplate.update(
                "UPDATE resource_health SET health_status = ? WHERE resource_id = ?", knownStatus, resourceId);

        updateResource(resourceId, "SERVICE", "{\"url\":\"https://example.com\"}");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT health_status FROM resource_health WHERE resource_id = ?", String.class, resourceId))
                .isEqualTo(knownStatus);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT health_status FROM monitors WHERE resource_id = ? AND type = 'TLS_CHECK'",
                        String.class,
                        resourceId))
                .isEqualTo("UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM resource_health_events WHERE resource_id = ?", Integer.class, resourceId))
                .isZero();
    }

    @Test
    void resolvesWholeSettingsLevelsAndDeletesBackToParent() throws Exception {
        long resourceId = createService("https://example.com");
        mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].source").value("APPLICATION"))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.intervalSeconds")
                        .value(30));

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

        jdbcTemplate.update(
                "UPDATE organization_memberships SET role = 'MEMBER' WHERE organization_id = ?", organizationId);
        mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk());
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
    void disabledProbeHasNoScheduleAndResourceOverrideEnablesSameInstance() throws Exception {
        putOrganization("PING", settings(false, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        long resourceId = createService("http://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'PING'", Long.class, resourceId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId))
                .isNull();
        assertThat(claimService.claimDue()).doesNotContain(monitorId);
        assertThat(executionPersistenceService.loadIfExecutable(monitorId)).isNull();

        putResource(resourceId, "PING", settings(true, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        assertThat(claimService.claimDue()).contains(monitorId);
        assertThat(executionPersistenceService.loadIfExecutable(monitorId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM monitors WHERE resource_id = ? AND type = 'PING'",
                        Integer.class,
                        resourceId))
                .isEqualTo(1);
    }

    @Test
    void schedulesNextRunFromCurrentEffectiveIntervalAtEverySettingsLevel() throws Exception {
        long resourceId = createService("https://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);

        assertNextRunUsesInterval(monitorId, 30);

        putOrganization("HTTP_CHECK", settings(true, 41, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        assertNextRunUsesInterval(monitorId, 41);

        putResource(resourceId, "HTTP_CHECK", settings(true, 52, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        assertNextRunUsesInterval(monitorId, 52);
    }

    @Test
    void settingsChangesAndFallbackReconcilePeriodicSchedule() throws Exception {
        long resourceId = createService("https://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);

        putOrganization("HTTP_CHECK", settings(false, 41, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        assertThat(nextRunAt(monitorId)).isNull();

        putResource(resourceId, "HTTP_CHECK", settings(true, 52, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        assertThat(nextRunAt(monitorId)).isNotNull();

        mockMvc.perform(delete("/api/resources/{id}/monitoring-settings/HTTP_CHECK", resourceId))
                .andExpect(status().isNoContent());
        assertThat(nextRunAt(monitorId)).isNull();

        mockMvc.perform(delete("/api/organizations/{id}/monitoring-settings/HTTP_CHECK", organizationId))
                .andExpect(status().isNoContent());
        assertThat(nextRunAt(monitorId)).isNotNull();
    }

    @Test
    void concurrentSchedulerClaimsReturnDueMonitorOnlyOnce() throws Exception {
        long resourceId = createService("https://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() + interval '1 hour'");
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() - interval '1 second' WHERE id = ?", monitorId);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<Long>> first = executor.submit(() -> claimAfter(start));
            Future<List<Long>> second = executor.submit(() -> claimAfter(start));
            start.countDown();

            long claimCount = List.of(first.get(), second.get()).stream()
                    .flatMap(List::stream)
                    .filter(id -> id == monitorId)
                    .count();
            assertThat(claimCount).isEqualTo(1);
        }
    }

    @Test
    void executionPrecheckDoesNotMovePeriodicSchedule() throws Exception {
        long resourceId = createService("https://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);
        Instant scheduled = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        jdbcTemplate.update("UPDATE monitors SET next_run_at = ? WHERE id = ?", Timestamp.from(scheduled), monitorId);

        assertThat(executionPersistenceService.loadIfExecutable(monitorId)).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId))
                .isEqualTo(scheduled);
    }

    @Test
    void requestedRunSchedulesNowAndRejectsDisabledOrIncompatibleMonitor() throws Exception {
        long resourceId = createService("https://example.com");
        long monitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'HTTP_CHECK'", Long.class, resourceId);
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() + interval '10 minutes' WHERE id = ?", monitorId);

        Instant beforeRequest = Instant.now();
        mockMvc.perform(post("/api/monitors/{id}/run", monitorId)).andExpect(status().isAccepted());
        assertThat(nextRunAt(monitorId)).isBetween(beforeRequest, Instant.now());
        assertThat(claimService.claimDue()).contains(monitorId);

        putResource(resourceId, "HTTP_CHECK", settings(false, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/monitors/{id}/run", monitorId)).andExpect(status().isConflict());

        putResource(resourceId, "HTTP_CHECK", settings(true, 30, 3, 2, "LATEST_ONLY", null, 500))
                .andExpect(status().isOk());
        updateResource(resourceId, "OTHER", "{}");
        mockMvc.perform(post("/api/monitors/{id}/run", monitorId)).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId))
                .isNull();
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
        long latestOnlyMonitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitors WHERE resource_id = ? AND type = 'PING'", Long.class, resourceId);
        long latestOnlyResultId = insertResult(latestOnlyMonitorId, "now() - interval '20 days'");

        assertThat(retentionService.deleteExpiredBatch()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT id FROM monitoring_results ORDER BY id", Long.class))
                .containsExactly(retainedId, latestOnlyResultId);
        assertThat(expiredId).isNotEqualTo(retainedId);
    }

    @Test
    void databaseKeepsStableSettingsRangesWithoutExtensibleTypeLists() throws Exception {
        long resourceId = createService("https://example.com");
        putResource(resourceId, "HTTP_CHECK", settings(true, 30, 3, 2, "HISTORY", 10, 500))
                .andExpect(status().isOk());

        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE connamespace = 'public'::regnamespace", String.class);
        assertThat(constraints)
                .doesNotContain(
                        "resources_type_check",
                        "tasks_type_check",
                        "monitors_type_check",
                        "organization_probe_settings_probe_type_check",
                        "resource_probe_settings_probe_type_check",
                        "credentials_type_check",
                        "resource_credentials_purpose_check");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE resource_probe_settings SET interval_seconds = 0 WHERE resource_id = ?", resourceId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void monitorTableContainsOnlyRuntimeState() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'monitors'
                """, String.class);
        assertThat(columns)
                .doesNotContain(
                        "enabled",
                        "interval_seconds",
                        "failure_threshold",
                        "recovery_threshold",
                        "storage_mode",
                        "retention_days");
    }

    private long createService(String url) throws Exception {
        String response = mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"name":"service-%d","type":"SERVICE","status":"ACTIVE","organizationId":%d,
                 "config":{"url":"%s"}}
                """.formatted(System.nanoTime(), organizationId, url)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void updateResource(long resourceId, String type, String config) throws Exception {
        mockMvc.perform(put("/api/resources/{id}", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"name":"updated","type":"%s","status":"ACTIVE","organizationId":%d,"config":%s}
                """.formatted(type, organizationId, config)))
                .andExpect(status().isOk());
    }

    private List<String> types(long resourceId) {
        return jdbcTemplate.queryForList("SELECT type FROM monitors WHERE resource_id = ?", String.class, resourceId);
    }

    private Instant nextRunAt(long monitorId) {
        return jdbcTemplate.queryForObject("SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId);
    }

    private long insertResult(long monitorId, String checkedAtExpression) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO monitoring_results (monitor_id, checked_at, result)
                VALUES (?, %s, '{}'::jsonb)
                RETURNING id
                """.formatted(checkedAtExpression), Long.class, monitorId);
    }

    private void assertNextRunUsesInterval(long monitorId, int intervalSeconds) {
        jdbcTemplate.update("UPDATE monitors SET next_run_at = now() - interval '1 second' WHERE id = ?", monitorId);
        Instant before = Instant.now();
        assertThat(claimService.claimDue()).contains(monitorId);
        Instant nextRunAt =
                jdbcTemplate.queryForObject("SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId);
        assertThat(nextRunAt)
                .isBetween(before.plusSeconds(intervalSeconds), Instant.now().plusSeconds(intervalSeconds + 2));
    }

    private List<Long> claimAfter(CountDownLatch start) throws InterruptedException {
        start.await();
        return claimService.claimDue();
    }

    private org.springframework.test.web.servlet.ResultActions putOrganization(String type, String body)
            throws Exception {
        return mockMvc.perform(put("/api/organizations/{id}/monitoring-settings/{type}", organizationId, type)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions putResource(long resourceId, String type, String body)
            throws Exception {
        return mockMvc.perform(put("/api/resources/{id}/monitoring-settings/{type}", resourceId, type)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String settings(
            boolean enabled,
            int interval,
            int failure,
            int recovery,
            String storage,
            Integer retention,
            Integer timeout) {
        return """
                {"enabled":%s,"intervalSeconds":%d,"failureThreshold":%d,"recoveryThreshold":%d,
                 "storageMode":"%s","retentionDays":%s,"timeoutMs":%s}
                """.formatted(
                        enabled,
                        interval,
                        failure,
                        recovery,
                        storage,
                        retention == null ? "null" : retention,
                        timeout == null ? "null" : timeout);
    }

    private void assertHttpSettings(
            long resourceId,
            String source,
            boolean enabled,
            int interval,
            int failure,
            int recovery,
            String storage,
            Integer retention,
            int timeout,
            boolean override)
            throws Exception {
        var result = mockMvc.perform(get("/api/resources/{id}/monitoring-settings", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].source").value(source))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.enabled")
                        .value(enabled))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.intervalSeconds")
                        .value(interval))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.failureThreshold")
                        .value(failure))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.recoveryThreshold")
                        .value(recovery))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.storageMode")
                        .value(storage))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.timeoutMs")
                        .value(timeout))
                .andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].resourceOverride")
                        .value(override));
        if (retention == null)
            result.andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.retentionDays")
                    .value(org.hamcrest.Matchers.contains((Object) null)));
        else
            result.andExpect(jsonPath("$[?(@.probeType == 'HTTP_CHECK')].effective.retentionDays")
                    .value(retention));
    }
}
