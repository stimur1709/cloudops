package com.github.stimur1709.cloudops.task.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
class TaskApiIntegrationTest {

    private static final long OTHER_USER_ID = 20_000L;
    private static HttpServer httpServer;
    private static ExecutorService httpExecutor;
    private static String baseUrl;
    private static volatile Runnable databaseProbe;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long organizationId;

    @BeforeAll
    static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/ok", exchange -> respond(exchange, 200, 80));
        httpServer.createContext("/unexpected", exchange -> respond(exchange, 503, 0));
        httpServer.createContext("/slow", exchange -> respond(exchange, 200, 400));
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
                TRUNCATE TABLE tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        insertUser(TestAuthentication.USER_ID, "current@example.com");
        insertUser(OTHER_USER_ID, "other@example.com");
        organizationId = insertOrganization("Current organization");
        addMember(organizationId, TestAuthentication.USER_ID, "OWNER");
        databaseProbe = () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void runsHttpCheckAndReturnsMeasuredCompletedTask() throws Exception {
        long resourceId = insertResource(organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/ok", 200, 1000);

        run(resourceId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/tasks/\\d+")))
                .andExpect(jsonPath("$.organizationId").value(organizationId))
                .andExpect(jsonPath("$.resourceId").value(resourceId))
                .andExpect(jsonPath("$.type").value("HTTP_CHECK"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdBy").value(TestAuthentication.USER_ID))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.result.url").value(baseUrl + "/ok"))
                .andExpect(jsonPath("$.result.statusCode").value(200))
                .andExpect(jsonPath("$.result.expectedStatus").value(200))
                .andExpect(jsonPath("$.result.responseTimeMs", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$.result.matchedExpectedStatus").value(true))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void statusMismatchIsCompleted() throws Exception {
        long resourceId = insertResource(
                organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/unexpected", 200, 1000
        );

        run(resourceId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.statusCode").value(503))
                .andExpect(jsonPath("$.result.matchedExpectedStatus").value(false));
    }

    @Test
    void timeoutIsControlledFailure() throws Exception {
        long resourceId = insertResource(
                organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/slow", 200, 100
        );

        run(resourceId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TIMEOUT"))
                .andExpect(jsonPath("$.errorMessage").value("HTTP check timed out"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void connectionRefusedIsControlledFailure() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        long resourceId = insertResource(
                organizationId, "service", "SERVICE", "ACTIVE",
                "http://127.0.0.1:" + unusedPort + "/unavailable", 200, 1000
        );

        run(resourceId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("CONNECTION_ERROR"));
    }

    @Test
    void doesNotHoldDatabaseConnectionDuringHttpCall() throws Exception {
        long resourceId = insertResource(
                organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/database-probe", 200, 1000
        );

        run(resourceId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.statusCode").value(200));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERVER", "DATABASE", "NETWORK_DEVICE", "OTHER"})
    void rejectsHttpCheckForOtherResourceTypes(String resourceType) throws Exception {
        long resourceId = insertResource(organizationId, "resource", resourceType, "ACTIVE", null, 200, 1000);

        run(resourceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TYPE_NOT_SUPPORTED"));
    }

    @Test
    void rejectsInactiveService() throws Exception {
        long resourceId = insertResource(
                organizationId, "service", "SERVICE", "INACTIVE", baseUrl + "/ok", 200, 1000
        );

        run(resourceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_INACTIVE"));
    }

    @Test
    void rejectsUnknownTaskTypeAndMissingType() throws Exception {
        long resourceId = insertResource(organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/ok", 200, 1000);

        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TLS_CHECK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("type"));
        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
    }

    @Test
    void memberCanRunAndGetTask() throws Exception {
        jdbcTemplate.update("UPDATE organization_memberships SET role = 'MEMBER' WHERE organization_id = ?", organizationId);
        long resourceId = insertResource(organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/unexpected", 200, 1000);
        String response = run(resourceId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long taskId = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void userFromAnotherOrganizationCannotRunOrGetTask() throws Exception {
        long resourceId = insertResource(organizationId, "service", "SERVICE", "ACTIVE", baseUrl + "/unexpected", 200, 1000);
        String response = run(resourceId).andReturn().getResponse().getContentAsString();
        long taskId = ((Number) JsonPath.read(response, "$.id")).longValue();
        long otherOrganization = insertOrganization("Other organization");
        addMember(otherOrganization, OTHER_USER_ID, "OWNER");

        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(OTHER_USER_ID))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"HTTP_CHECK\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/{id}", taskId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(OTHER_USER_ID)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesWithWhitelistSortingAndMandatoryMembershipScope() throws Exception {
        long firstResource = insertResource(
                organizationId, "first", "SERVICE", "ACTIVE", baseUrl + "/unexpected", 200, 1000
        );
        run(firstResource).andExpect(status().isCreated());
        long otherOrganization = insertOrganization("Other organization");
        addMember(otherOrganization, OTHER_USER_ID, "OWNER");
        long otherResource = insertResource(
                otherOrganization, "other", "SERVICE", "ACTIVE", baseUrl + "/unexpected", 200, 1000
        );
        insertCompletedTask(otherOrganization, otherResource, OTHER_USER_ID);

        mockMvc.perform(post("/api/tasks/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "start": 0,
                                  "size": 20,
                                  "getTotal": true,
                                  "filter": {"operator": "OR", "conditions": [
                                    {"field": "organizationId", "operation": "EQ", "value": "%d"},
                                    {"field": "organizationId", "operation": "EQ", "value": "%d"}
                                  ]},
                                  "sort": [{"field": "createdAt", "order": "DESC"}]
                                }
                                """.formatted(organizationId, otherOrganization)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[*].organizationId", contains((int) organizationId)))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(post("/api/tasks/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":20,"getTotal":false,
                                 "filter":{"operator":"AND","conditions":[
                                   {"field":"result","operation":"EQ","value":"x"}]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Unknown filter field: result"));
    }

    private ResultActions run(long resourceId) throws Exception {
        return mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"HTTP_CHECK\"}"));
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, ?, 'User', '{noop}unused', now(), now())
                """, id, email);
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id
                """, Long.class, name);
    }

    private void addMember(long organization, long user, String role) {
        jdbcTemplate.update("""
                INSERT INTO organization_memberships (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, organization, user, role);
    }

    private long insertResource(
            long organization, String name, String type, String status, String url, int expectedStatus, int timeoutMs
    ) {
        String config = type.equals("SERVICE")
                ? "{\"url\":\"%s\",\"expectedStatus\":%d,\"timeoutMs\":%d}"
                        .formatted(url, expectedStatus, timeoutMs)
                : "{}";
        return jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), now(), now()) RETURNING id
                """, Long.class, name, type, status, organization, config);
    }

    private void insertCompletedTask(long organization, long resource, long user) {
        jdbcTemplate.update("""
                INSERT INTO tasks
                    (organization_id, resource_id, type, status, created_by, created_at, started_at, completed_at, result)
                VALUES (?, ?, 'HTTP_CHECK', 'COMPLETED', ?, ?, ?, ?, CAST('{}' AS jsonb))
                """, organization, resource, user,
                Timestamp.from(Instant.parse("2026-08-27T01:00:00Z")),
                Timestamp.from(Instant.parse("2026-08-27T01:00:01Z")),
                Timestamp.from(Instant.parse("2026-08-27T01:00:02Z")));
    }

    private static void respond(HttpExchange exchange, int status, long delayMs) throws IOException {
        try (exchange) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
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
