package com.github.stimur1709.cloudops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@SpringBootTest(properties = "cloudops.task.outbox.enabled=false")
class TaskApiIntegrationTest {

    private static final long OTHER_USER_ID = 20_000L;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private long organizationId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings,
                    monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks,
                    organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        insertUser(TestAuthentication.USER_ID, "current@example.com");
        insertUser(OTHER_USER_ID, "other@example.com");
        organizationId = insertOrganization("Current organization");
        addMember(organizationId, TestAuthentication.USER_ID, "OWNER");
        resourceId = insertResource(organizationId, "SERVER", "ACTIVE", "{\"host\":\"127.0.0.1\"}");
        bindSshCredential(organizationId, resourceId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP_CHECK", "PORT_CHECK", "DNS_CHECK", "PING", "TLS_CHECK", "SSH_CHECK"})
    void rejectsProbeTypesWithoutCreatingTaskOrOutbox(String type) throws Exception {
        mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"parameters\":{}}".formatted(type)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("type"));

        assertNothingCreated();
    }

    @Test
    void ownerCreatesRunCommandAndGetAndSearchReturnParameters() throws Exception {
        String response = createTask("{\"type\":\"RUN_COMMAND\",\"parameters\":{\"command\":\"uname -a\"}}")
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/tasks/\\d+")))
                .andExpect(jsonPath("$.type").value("RUN_COMMAND"))
                .andExpect(jsonPath("$.parameters.command").value("uname -a"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long taskId = ((Number) JsonPath.read(response, "$.id")).longValue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT parameters ->> 'command' FROM tasks WHERE id = ?", String.class, taskId))
                .isEqualTo("uname -a");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class))
                .isOne();
        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters.command").value("uname -a"));
        mockMvc.perform(post("/api/tasks/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":0,\"size\":10,\"getTotal\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].parameters.command").value("uname -a"));
    }

    @Test
    void adminCanCreateButMemberCannot() throws Exception {
        jdbcTemplate.update(
                "UPDATE organization_memberships SET role = 'ADMIN' WHERE organization_id = ? AND user_id = ?",
                organizationId,
                TestAuthentication.USER_ID);
        createTask(validRequest()).andExpect(status().isAccepted());

        jdbcTemplate.update(
                "UPDATE organization_memberships SET role = 'MEMBER' WHERE organization_id = ? AND user_id = ?",
                organizationId,
                TestAuthentication.USER_ID);
        createTask(validRequest()).andExpect(status().isForbidden());
    }

    @Test
    void foreignResourceIsNotFound() throws Exception {
        long otherOrganization = insertOrganization("Other organization");
        addMember(otherOrganization, OTHER_USER_ID, "OWNER");

        createTask(validRequest(), OTHER_USER_ID).andExpect(status().isNotFound());
    }

    @Test
    void unavailableResourceConfigurationsAreControlledConflicts() throws Exception {
        jdbcTemplate.update("UPDATE resources SET status = 'INACTIVE' WHERE id = ?", resourceId);
        createTask(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_INACTIVE"));

        jdbcTemplate.update(
                "UPDATE resources SET status = 'ACTIVE', type = 'OTHER', config = '{}'::jsonb WHERE id = ?",
                resourceId);
        createTask(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_UNSUPPORTED"));
    }

    @Test
    void missingSshCredentialIsControlledConflict() throws Exception {
        jdbcTemplate.update("DELETE FROM resource_credentials WHERE resource_id = ?", resourceId);

        createTask(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SSH_CREDENTIAL_NOT_CONFIGURED"));
        assertNothingCreated();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\":\"RUN_COMMAND\"}",
                "{\"type\":\"RUN_COMMAND\",\"parameters\":{}}",
                "{\"type\":\"RUN_COMMAND\",\"parameters\":{\"command\":\"   \"}}",
                "{\"type\":\"RUN_COMMAND\",\"parameters\":{\"command\":\"true\",\"unknown\":1}}"
            })
    void invalidParametersDoNotCreateTaskOrOutbox(String request) throws Exception {
        createTask(request).andExpect(status().isBadRequest());
        assertNothingCreated();
    }

    @Test
    void tooLongCommandDoesNotCreateTaskOrOutbox() throws Exception {
        createTask("{\"type\":\"RUN_COMMAND\",\"parameters\":{\"command\":\"%s\"}}".formatted("x".repeat(4097)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parameters.command"));
        assertNothingCreated();
    }

    private org.springframework.test.web.servlet.ResultActions createTask(String request) throws Exception {
        return createTask(request, TestAuthentication.USER_ID);
    }

    private org.springframework.test.web.servlet.ResultActions createTask(String request, long userId)
            throws Exception {
        return mockMvc.perform(post("/api/resources/{id}/tasks", resourceId)
                .with(jwt().jwt(token -> token.subject(Long.toString(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private String validRequest() {
        return "{\"type\":\"RUN_COMMAND\",\"parameters\":{\"command\":\"true\"}}";
    }

    private void assertNothingCreated() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class))
                .isZero();
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

    private long insertResource(long organization, String type, String statusValue, String config) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('resource', ?, ?, ?, CAST(? AS jsonb), now(), now()) RETURNING id
                """, Long.class, type, statusValue, organization, config);
    }

    private void bindSshCredential(long organization, long resource) {
        long credentialId = jdbcTemplate.queryForObject("""
                INSERT INTO credentials
                    (organization_id, name, type, username, secret_encrypted, created_at, updated_at)
                VALUES (?, 'SSH', 'USERNAME_PASSWORD', 'cloudops', 'unused', now(), now()) RETURNING id
                """, Long.class, organization);
        jdbcTemplate.update("""
                INSERT INTO resource_credentials (resource_id, credential_id, purpose) VALUES (?, ?, 'SSH')
                """, resource, credentialId);
    }
}
