package com.github.stimur1709.cloudops.task.api;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
class TaskApiIntegrationTest {

    private static final long OTHER_USER_ID = 20_000L;

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private long organizationId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health_events, resource_health,
                outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        insertUser(TestAuthentication.USER_ID, "current@example.com");
        insertUser(OTHER_USER_ID, "other@example.com");
        organizationId = insertOrganization("Current organization");
        addMember(organizationId, TestAuthentication.USER_ID, "OWNER");
        resourceId = insertResource(organizationId, "ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP_CHECK", "PORT_CHECK", "DNS_CHECK", "PING", "TLS_CHECK"})
    void rejectsFormerProbeTaskTypesWithoutCreatingTaskOrOutbox(String type) throws Exception {
        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\"}".formatted(type)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("type"));

        assertThat(count("tasks")).isZero();
        assertThat(count("outbox_messages")).isZero();
    }

    @Test
    void testOnlyOperationExercisesGenericTaskPipeline() throws Exception {
        String response = mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEST_OPERATION\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/tasks/\\d+")))
                .andExpect(jsonPath("$.type").value("TEST_OPERATION"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long taskId = ((Number) JsonPath.read(response, "$.id")).longValue();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/tasks/{id}", taskId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.result.operation").value("test"))
                        .andExpect(jsonPath("$.attemptCount").value(1))
        );
    }

    @Test
    void missingTypeIsValidationError() throws Exception {
        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
    }

    @Test
    void foreignUserCannotCreateOrReadTask() throws Exception {
        long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (organization_id, resource_id, type, status, created_by, created_at)
                VALUES (?, ?, 'TEST_OPERATION', 'PENDING', ?, now()) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID);
        long otherOrganization = insertOrganization("Other organization");
        addMember(otherOrganization, OTHER_USER_ID, "OWNER");

        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(OTHER_USER_ID))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEST_OPERATION\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/{id}", taskId)
                        .with(jwt().jwt(token -> token.subject(Long.toString(OTHER_USER_ID)))))
                .andExpect(status().isNotFound());
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

    private long insertResource(long organization, String resourceStatus) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('resource', 'OTHER', ?, ?, '{}'::jsonb, now(), now()) RETURNING id
                """, Long.class, resourceStatus, organization);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
