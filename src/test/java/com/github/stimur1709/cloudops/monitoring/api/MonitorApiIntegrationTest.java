package com.github.stimur1709.cloudops.monitoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.stimur1709.cloudops.SqlStatementRecorder;
import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorClaimService;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionService;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorScheduler;
import com.github.stimur1709.cloudops.monitoring.retention.MonitoringRetentionService;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "cloudops.monitoring.scheduler-enabled=true",
        "cloudops.monitoring.poll-interval=1h",
        "cloudops.monitoring.retention-poll-interval=1h"
})
class MonitorApiIntegrationTest {

    private static HttpServer httpServer;
    private static ExecutorService httpExecutor;
    private static String baseUrl;
    private static volatile Runnable databaseProbe;

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MonitorScheduler scheduler;
    @Autowired private MonitorClaimService claimService;
    @Autowired private MonitorExecutionService executionService;
    @Autowired private MonitoringRetentionService retentionService;
    @Autowired private SqlStatementRecorder sqlStatementRecorder;

    private MockMvc mockMvc;
    private long organizationId;

    @BeforeAll
    static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/ok", exchange -> respond(exchange, 200, 0));
        httpServer.createContext("/unexpected", exchange -> respond(exchange, 503, 0));
        httpServer.createContext("/slow", exchange -> respond(exchange, 200, 500));
        httpServer.createContext("/database-probe", exchange -> {
            databaseProbe.run();
            respond(exchange, 200, 0);
        });
        httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
        httpServer.setExecutor(httpExecutor);
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopHttpServer() {
        httpServer.stop(0);
        httpExecutor.close();
    }

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks,
                    organization_memberships, resources, users, organizations RESTART IDENTITY
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
        databaseProbe = () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void createsHttpMonitorForActiveServiceAndRejectsDuplicate() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);

        String response = create(resourceId, "HISTORY", 30, "30")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/monitors/\\d+")))
                .andExpect(jsonPath("$.resourceId").value(resourceId))
                .andExpect(jsonPath("$.type").value("HTTP_CHECK"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.storageMode").value("HISTORY"))
                .andExpect(jsonPath("$.healthStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.failureThreshold").value(3))
                .andExpect(jsonPath("$.recoveryThreshold").value(2))
                .andExpect(jsonPath("$.consecutiveFailures").doesNotExist())
                .andExpect(jsonPath("$.consecutiveSuccesses").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(monitorId(response)).isPositive();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT health_status, consecutive_failures, consecutive_successes FROM monitors
                """))
                .containsEntry("health_status", "UNKNOWN")
                .containsEntry("consecutive_failures", 0)
                .containsEntry("consecutive_successes", 0);
        create(resourceId, "LATEST_ONLY", null, "30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONITOR_ALREADY_EXISTS"));
    }

    @Test
    void createsAndExecutesPortMonitorThroughExistingHealthPipeline() throws Exception {
        int port = httpServer.getAddress().getPort();
        long resourceId = insertResource(
                "SERVER", "ACTIVE", "{\"host\":\"127.0.0.1\",\"port\":%d}".formatted(port)
        );
        long monitorId = monitorId(create(resourceId, "PORT_CHECK", "HISTORY", 30, "30", 1, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PORT_CHECK"))
                .andReturn().getResponse().getContentAsString());

        executionService.execute(monitorId);

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.success").value(true))
                .andExpect(jsonPath("$[0].lastResult.data.host").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].lastResult.data.port").value(port))
                .andExpect(jsonPath("$[0].lastResult.data.responseTimeMs").isNumber())
                .andExpect(jsonPath("$[0].healthStatus").value("UP"));
        assertResourceHealth(resourceId, "UP");
    }

    @Test
    void createsAndExecutesDnsAndPingMonitorsThroughExistingHealthPipeline() throws Exception {
        long resourceId = insertResource("SERVER", "ACTIVE", "{\"host\":\"localhost\"}");
        long dnsMonitorId = monitorId(create(resourceId, "DNS_CHECK", "HISTORY", 30, "30", 1, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DNS_CHECK"))
                .andReturn().getResponse().getContentAsString());
        long pingMonitorId = monitorId(create(resourceId, "PING", "HISTORY", 30, "30", 1, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PING"))
                .andReturn().getResponse().getContentAsString());

        executionService.execute(dnsMonitorId);
        executionService.execute(pingMonitorId);

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'DNS_CHECK')].lastResult.data.hostname").value("localhost"))
                .andExpect(jsonPath("$[?(@.type == 'PING')].lastResult.data.host").value("localhost"));
        assertResourceHealth(resourceId, "UP");
    }

    @Test
    void tlsMonitorUsesExistingThresholdAndHealthPipeline() throws Exception {
        int plainHttpPort = httpServer.getAddress().getPort();
        long resourceId = insertResource(
                "SERVER", "ACTIVE", "{\"host\":\"127.0.0.1\",\"port\":%d}".formatted(plainHttpPort)
        );
        long monitorId = monitorId(create(resourceId, "TLS_CHECK", "LATEST_ONLY", null, "30", 1, 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        executionService.execute(monitorId);

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.error.code").isString())
                .andExpect(jsonPath("$[0].healthStatus").value("DOWN"));
        assertResourceHealth(resourceId, "DOWN");
    }

    @Test
    void failedPortMonitorUsesExistingThresholdsAndResourceHealthAggregation() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        long resourceId = insertResource(
                "DATABASE", "ACTIVE",
                "{\"host\":\"127.0.0.1\",\"port\":%d,\"database\":\"cloudops\"}".formatted(unusedPort)
        );
        long monitorId = monitorId(create(resourceId, "PORT_CHECK", "LATEST_ONLY", null, "30", 1, 1)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        executionService.execute(monitorId);

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.success").value(false))
                .andExpect(jsonPath("$[0].lastResult.error.code").value("CONNECTION_ERROR"))
                .andExpect(jsonPath("$[0].healthStatus").value("DOWN"));
        assertResourceHealth(resourceId, "DOWN");
    }

    @Test
    void rejectsPortMonitorWhenResourceConfigHasNoPort() throws Exception {
        long resourceId = insertResource("NETWORK_DEVICE", "ACTIVE", "{\"host\":\"switch\"}");

        create(resourceId, "PORT_CHECK", "LATEST_ONLY", null, "30", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONITOR_TYPE_NOT_SUPPORTED"));
    }

    @Test
    void rejectsUnsupportedProbeInactiveResourceAndInvalidSettings() throws Exception {
        long serverId = insertResource("SERVER", "ACTIVE", "{}");
        create(serverId, "LATEST_ONLY", null, "30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONITOR_TYPE_NOT_SUPPORTED"));

        long inactiveId = insertService("INACTIVE", baseUrl + "/ok", 200, 1000);
        create(inactiveId, "LATEST_ONLY", null, "30")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_INACTIVE"));

        long serviceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        create(serviceId, "LATEST_ONLY", null, "29")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intervalSeconds"));
        create(serviceId, "HISTORY", null, "30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("retentionDays"));
        create(serviceId, "LATEST_ONLY", 10, "30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("retentionDays"));

        mockMvc.perform(post("/api/resources/{id}/monitors", serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"HTTP_CHECK","intervalSeconds":30,"enabled":true,
                                 "storageMode":"LATEST_ONLY","retentionDays":null,
                                 "failureThreshold":0,"recoveryThreshold":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'failureThreshold')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'recoveryThreshold')]").exists());
    }

    @Test
    void rejectsInvalidSettingsOnUpdate() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30")
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalSeconds":29,"enabled":true,"storageMode":"HISTORY","retentionDays":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intervalSeconds"))
                .andExpect(jsonPath("$.errors[1].field").value("retentionDays"));
    }

    @Test
    void schedulerStoresCompletedSuccessAndHistoryWithoutTaskOrOutbox() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/database-probe", 200, 1000);
        long monitorId = monitorId(create(resourceId, "HISTORY", 30, "30")
                .andReturn().getResponse().getContentAsString());

        scheduler.poll();

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.success").value(true))
                .andExpect(jsonPath("$[0].lastResult.data.statusCode").value(200))
                .andExpect(jsonPath("$[0].lastResult.data.matchedExpectedStatus").value(true));
        assertThat(count("monitoring_results")).isEqualTo(1);
        assertThat(count("tasks")).isZero();
        assertThat(count("outbox_messages")).isZero();

        mockMvc.perform(post("/api/monitors/{id}/results/search", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":20,"sort":[{"field":"checkedAt","order":"DESC"}],
                                "getTotal":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].result.success").value(true));
    }

    @Test
    void executionDoesNotReloadResourceWhenSavingResult() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30")
                .andReturn().getResponse().getContentAsString());
        sqlStatementRecorder.clear();

        executionService.execute(monitorId);

        assertThat(sqlStatementRecorder.statements().stream()
                .filter(statement -> statement.toLowerCase().contains(" from resources ")))
                .hasSize(1);
        assertThat(sqlStatementRecorder.statements().stream()
                .filter(statement -> statement.toLowerCase().contains(" from monitors ")))
                .hasSize(3);
    }

    @Test
    void statusMismatchIsCompletedFailureWhileLatestOnlyCreatesNoHistory() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/unexpected", 200, 1000);
        create(resourceId, "LATEST_ONLY", null, "30");

        scheduler.poll();

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.success").value(false))
                .andExpect(jsonPath("$[0].lastResult.data.statusCode").value(503))
                .andExpect(jsonPath("$[0].lastResult.data.matchedExpectedStatus").value(false));
        assertThat(count("monitoring_results")).isZero();
    }

    @Test
    void timeoutIsStoredAsFailedProbeResult() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/slow", 200, 50);
        create(resourceId, "LATEST_ONLY", null, "30");

        scheduler.poll();

        mockMvc.perform(get("/api/resources/{id}/monitors", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastResult.success").value(false))
                .andExpect(jsonPath("$[0].lastResult.error.code").value("TIMEOUT"))
                .andExpect(jsonPath("$[0].lastResult.error.message").value("HTTP check timed out"))
                .andExpect(jsonPath("$[0].healthStatus").value("DOWN"));
    }

    @Test
    void appliesHealthThresholdsEquallyToLatestOnlyAndHistory() throws Exception {
        for (String storageMode : List.of("LATEST_ONLY", "HISTORY")) {
            long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
            Integer retentionDays = storageMode.equals("HISTORY") ? 30 : null;
            long monitorId = monitorId(create(resourceId, storageMode, retentionDays, "30", 2, 2)
                    .andExpect(jsonPath("$.failureThreshold").value(2))
                    .andExpect(jsonPath("$.recoveryThreshold").value(2))
                    .andReturn().getResponse().getContentAsString());

            executionService.execute(monitorId);
            assertHealth(monitorId, "UP", 0, 0);

            updateServiceConfig(resourceId, baseUrl + "/unexpected", 200, 1000);
            executionService.execute(monitorId);
            assertHealth(monitorId, "UP", 1, 0);
            executionService.execute(monitorId);
            assertHealth(monitorId, "DOWN", 0, 0);

            updateServiceConfig(resourceId, baseUrl + "/ok", 200, 1000);
            executionService.execute(monitorId);
            assertHealth(monitorId, "DOWN", 0, 1);
            executionService.execute(monitorId);
            assertHealth(monitorId, "UP", 0, 0);

            int historyCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM monitoring_results WHERE monitor_id = ?", Integer.class, monitorId
            );
            assertThat(historyCount).isEqualTo(storageMode.equals("HISTORY") ? 5 : 0);
        }
    }

    @Test
    void disabledMonitorPreservesHealthAndCounters() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30", 2, 2)
                .andReturn().getResponse().getContentAsString());
        executionService.execute(monitorId);
        updateServiceConfig(resourceId, baseUrl + "/unexpected", 200, 1000);
        executionService.execute(monitorId);
        assertHealth(monitorId, "UP", 1, 0);

        jdbcTemplate.update("UPDATE monitors SET enabled = false WHERE id = ?", monitorId);
        executionService.execute(monitorId);

        assertHealth(monitorId, "UP", 1, 0);
    }

    @Test
    void monitorLifecycleRecalculatesResourceHealth() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30")
                .andReturn().getResponse().getContentAsString());
        assertResourceHealth(resourceId, "UNKNOWN");

        executionService.execute(monitorId);
        assertResourceHealth(resourceId, "UP");

        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalSeconds":30,"enabled":false,"storageMode":"LATEST_ONLY",
                                 "retentionDays":null}
                                """))
                .andExpect(status().isOk());
        assertResourceHealth(resourceId, "UNKNOWN");

        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalSeconds":30,"enabled":true,"storageMode":"LATEST_ONLY",
                                 "retentionDays":null}
                                """))
                .andExpect(status().isOk());
        assertResourceHealth(resourceId, "UP");

        mockMvc.perform(delete("/api/monitors/{id}", monitorId))
                .andExpect(status().isNoContent());
        assertResourceHealth(resourceId, "UNKNOWN");
    }

    @Test
    void resourceHealthHistoryContainsOnlyRealTransitionsInOrder() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30", 1, 1)
                .andReturn().getResponse().getContentAsString());

        executionService.execute(monitorId);
        executionService.execute(monitorId);
        updateServiceConfig(resourceId, baseUrl + "/unexpected", 200, 1000);
        executionService.execute(monitorId);
        updateServiceConfig(resourceId, baseUrl + "/ok", 200, 1000);
        executionService.execute(monitorId);
        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalSeconds":30,"enabled":false,"storageMode":"LATEST_ONLY",
                                 "retentionDays":null,"failureThreshold":1,"recoveryThreshold":1}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList("""
                SELECT from_status || '->' || to_status
                FROM resource_health_events WHERE resource_id = ? ORDER BY id
                """, String.class, resourceId))
                .containsExactly("UNKNOWN->UP", "UP->DOWN", "DOWN->UP", "UP->UNKNOWN");
    }

    @Test
    void searchesResourceHealthHistoryWithinResourceAndReturnsForeignResourceAsNotFound() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long foreignOrganizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Foreign organization', now(), now()) RETURNING id
                """, Long.class);
        long foreignResourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('foreign', 'SERVICE', 'ACTIVE', ?, '{}'::jsonb, now(), now()) RETURNING id
                """, Long.class, foreignOrganizationId);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UP')",
                foreignResourceId
        );
        jdbcTemplate.update("""
                INSERT INTO resource_health_events (resource_id, from_status, to_status, changed_at)
                VALUES (?, 'UNKNOWN', 'UP', '2026-08-28T01:00:00Z'),
                       (?, 'UNKNOWN', 'UP', '2026-08-28T02:00:00Z')
                """, resourceId, foreignResourceId);

        mockMvc.perform(post("/api/resources/{id}/health/events/search", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":20,
                                 "filter":{"operator":"AND","conditions":[
                                   {"field":"toStatus","operation":"EQ","value":"UP"}]},
                                 "sort":[{"field":"changedAt","order":"DESC"}],"getTotal":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fromStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].toStatus").value("UP"))
                .andExpect(jsonPath("$.items[0].changedAt").value("2026-08-28T01:00:00Z"));

        mockMvc.perform(post("/api/resources/{id}/health/events/search", foreignResourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":20,"getTotal":false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveResourceKeepsOperationalHealthSeparate() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30")
                .andReturn().getResponse().getContentAsString());
        executionService.execute(monitorId);
        jdbcTemplate.update("UPDATE resources SET status = 'INACTIVE' WHERE id = ?", resourceId);

        mockMvc.perform(get("/api/resources/{id}", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.healthStatus").value("UP"));
    }

    @Test
    void updatingThresholdsPreservesHealthAndResetsCounters() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "LATEST_ONLY", null, "30")
                .andReturn().getResponse().getContentAsString());
        executionService.execute(monitorId);
        updateServiceConfig(resourceId, baseUrl + "/unexpected", 200, 1000);
        executionService.execute(monitorId);
        assertHealth(monitorId, "UP", 1, 0);

        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalSeconds":30,"enabled":true,"storageMode":"LATEST_ONLY",
                                 "retentionDays":null,"failureThreshold":4,"recoveryThreshold":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthStatus").value("UP"))
                .andExpect(jsonPath("$.failureThreshold").value(4))
                .andExpect(jsonPath("$.recoveryThreshold").value(3));
        assertHealth(monitorId, "UP", 0, 0);
    }

    @Test
    void inactiveResourceIsClaimedButNotExecuted() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        create(resourceId, "HISTORY", 30, "30");
        jdbcTemplate.update("UPDATE resources SET status = 'INACTIVE' WHERE id = ?", resourceId);

        scheduler.poll();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_checked_at IS NULL FROM monitors", Boolean.class
        )).isTrue();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT health_status, consecutive_failures, consecutive_successes FROM monitors
                """))
                .containsEntry("health_status", "UNKNOWN")
                .containsEntry("consecutive_failures", 0)
                .containsEntry("consecutive_successes", 0);
        assertThat(count("monitoring_results")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_run_at > now() FROM monitors", Boolean.class
        )).isTrue();
    }

    @Test
    void switchingToLatestOnlyDeletesHistory() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = monitorId(create(resourceId, "HISTORY", 30, "30")
                .andReturn().getResponse().getContentAsString());
        scheduler.poll();
        assertThat(count("monitoring_results")).isEqualTo(1);

        mockMvc.perform(put("/api/monitors/{id}", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalSeconds\":60,\"enabled\":true,\"storageMode\":\"LATEST_ONLY\",\"retentionDays\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageMode").value("LATEST_ONLY"))
                .andExpect(jsonPath("$.retentionDays").doesNotExist());
        assertThat(count("monitoring_results")).isZero();
    }

    @Test
    void twoConcurrentClaimsDoNotClaimSameTickTwice() throws Exception {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        create(resourceId, "LATEST_ONLY", null, "30");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<Long>> first = executor.submit(claimService::claimDue);
            Future<List<Long>> second = executor.submit(claimService::claimDue);
            assertThat(first.get().size() + second.get().size()).isEqualTo(1);
        }
    }

    @Test
    void retentionDeletesOnlyExpiredHistoryInBoundedCleanup() {
        long resourceId = insertService("ACTIVE", baseUrl + "/ok", 200, 1000);
        long monitorId = insertMonitor(resourceId, "HISTORY", 1);
        jdbcTemplate.update("""
                INSERT INTO monitoring_results (monitor_id, checked_at, result)
                VALUES (?, now() - INTERVAL '2 days', '{}'::jsonb), (?, now(), '{}'::jsonb)
                """, monitorId, monitorId);

        assertThat(retentionService.deleteExpiredBatch()).isEqualTo(1);
        assertThat(count("monitoring_results")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            long resourceId, String storageMode, Integer retentionDays, String intervalSeconds
    ) throws Exception {
        return create(resourceId, storageMode, retentionDays, intervalSeconds, null, null);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            long resourceId,
            String storageMode,
            Integer retentionDays,
            String intervalSeconds,
            Integer failureThreshold,
            Integer recoveryThreshold
    ) throws Exception {
        return create(resourceId, "HTTP_CHECK", storageMode, retentionDays, intervalSeconds,
                failureThreshold, recoveryThreshold);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            long resourceId,
            String type,
            String storageMode,
            Integer retentionDays,
            String intervalSeconds,
            Integer failureThreshold,
            Integer recoveryThreshold
    ) throws Exception {
        String retention = retentionDays == null ? "null" : retentionDays.toString();
        String failure = failureThreshold == null ? "null" : failureThreshold.toString();
        String recovery = recoveryThreshold == null ? "null" : recoveryThreshold.toString();
        return mockMvc.perform(post("/api/resources/{id}/monitors", resourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"%s","intervalSeconds":%s,"enabled":true,
                         "storageMode":"%s","retentionDays":%s,
                         "failureThreshold":%s,"recoveryThreshold":%s}
                        """.formatted(type, intervalSeconds, storageMode, retention, failure, recovery)));
    }

    private void updateServiceConfig(long resourceId, String url, int expectedStatus, int timeoutMs) {
        jdbcTemplate.update(
                "UPDATE resources SET config = CAST(? AS jsonb) WHERE id = ?",
                "{\"url\":\"%s\",\"expectedStatus\":%d,\"timeoutMs\":%d}"
                        .formatted(url, expectedStatus, timeoutMs),
                resourceId
        );
    }

    private void assertHealth(long monitorId, String healthStatus, int failures, int successes) {
        assertThat(jdbcTemplate.queryForMap("""
                SELECT health_status, consecutive_failures, consecutive_successes
                FROM monitors WHERE id = ?
                """, monitorId))
                .containsEntry("health_status", healthStatus)
                .containsEntry("consecutive_failures", failures)
                .containsEntry("consecutive_successes", successes);
    }

    private void assertResourceHealth(long resourceId, String healthStatus) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT health_status FROM resource_health WHERE resource_id = ?",
                String.class,
                resourceId
        )).isEqualTo(healthStatus);
    }

    private long insertService(String status, String url, int expectedStatus, int timeoutMs) {
        return insertResource(
                "SERVICE", status,
                "{\"url\":\"%s\",\"expectedStatus\":%d,\"timeoutMs\":%d}"
                        .formatted(url, expectedStatus, timeoutMs)
        );
    }

    private long insertResource(String type, String status, String config) {
        long resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), now(), now()) RETURNING id
                """, Long.class, "resource-" + System.nanoTime(), type, status, organizationId, config);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UNKNOWN')",
                resourceId
        );
        return resourceId;
    }

    private long insertMonitor(long resourceId, String storageMode, Integer retentionDays) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO monitors
                    (resource_id, type, enabled, interval_seconds, next_run_at, storage_mode, retention_days)
                VALUES (?, 'HTTP_CHECK', true, 30, now(), ?, ?) RETURNING id
                """, Long.class, resourceId, storageMode, retentionDays);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private long monitorId(String response) {
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static void respond(HttpExchange exchange, int status, long delayMs) throws IOException {
        try (exchange) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(Duration.ofMillis(delayMs));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
