package com.github.stimur1709.cloudops.organization.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthorizationApiIntegrationTest {

    private static final long ADMIN_ID = 10_001L;
    private static final long MEMBER_ID = 10_002L;
    private static final long OUTSIDER_ID = 10_003L;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        insertUser(TestAuthentication.USER_ID, "owner@example.com");
        insertUser(ADMIN_ID, "admin@example.com");
        insertUser(MEMBER_ID, "member@example.com");
        insertUser(OUTSIDER_ID, "outsider@example.com");
    }

    @Test
    void organizationRolesAndHiddenNotFoundAreEnforced() throws Exception {
        long organization = insertOrganization("Platform");
        insertMembership(organization, TestAuthentication.USER_ID, "OWNER");
        insertMembership(organization, ADMIN_ID, "ADMIN");
        insertMembership(organization, MEMBER_ID, "MEMBER");

        mockMvc.perform(get("/api/organizations/{id}", organization).with(as(MEMBER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/organizations/{id}", organization).with(as(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/organizations/{id}", organization).with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/organizations/{id}", organization).with(as(ADMIN_ID)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations/{id}", organization).with(as(OUTSIDER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void organizationSearchScopeAppliesToItemsAndTotalAndCannotBeBypassed() throws Exception {
        long visible = insertOrganization("Visible");
        long hidden = insertOrganization("Hidden");
        insertMembership(visible, TestAuthentication.USER_ID, "OWNER");

        mockMvc.perform(post("/api/organizations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":10,"filter":{"operator":"OR","conditions":[
                                  {"field":"id","operation":"EQ","value":"%d"},
                                  {"field":"name","operation":"EQ","value":"Visible"}
                                ]},"getTotal":true}
                                """.formatted(hidden)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(visible))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void resourceRolesSearchScopeAndHiddenNotFoundAreEnforced() throws Exception {
        long visibleOrganization = insertOrganization("Visible");
        long hiddenOrganization = insertOrganization("Hidden");
        insertMembership(visibleOrganization, TestAuthentication.USER_ID, "OWNER");
        insertMembership(visibleOrganization, ADMIN_ID, "ADMIN");
        insertMembership(visibleOrganization, MEMBER_ID, "MEMBER");
        long visibleResource = insertResource("visible", visibleOrganization);
        long hiddenResource = insertResource("hidden", hiddenOrganization);

        mockMvc.perform(post("/api/resources").with(as(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("member-create", visibleOrganization)))
                .andExpect(status().isForbidden());
        String created = mockMvc.perform(post("/api/resources").with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("admin-create", visibleOrganization)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long createdId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
        mockMvc.perform(delete("/api/resources/{id}", createdId).with(as(MEMBER_ID)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/resources/{id}", createdId).with(as(ADMIN_ID)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resources/{id}", visibleResource).with(as(MEMBER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/resources/{id}", visibleResource).with(as(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("denied", visibleOrganization)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/resources/{id}", visibleResource).with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("updated", visibleOrganization)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/resources/{id}", hiddenResource))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));

        mockMvc.perform(post("/api/resources/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":10,"filter":{"operator":"OR","conditions":[
                                  {"field":"id","operation":"EQ","value":"%d"},
                                  {"field":"name","operation":"EQ","value":"updated"}
                                ]},"getTotal":true}
                                """.formatted(hiddenResource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(visibleResource))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void movingResourceRequiresManagerRoleInBothOrganizations() throws Exception {
        long source = insertOrganization("Source");
        long target = insertOrganization("Target");
        insertMembership(source, ADMIN_ID, "ADMIN");
        insertMembership(target, ADMIN_ID, "MEMBER");
        long resource = insertResource("server", source);

        mockMvc.perform(put("/api/resources/{id}", resource).with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(resourceBody("server", target)))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("""
                UPDATE organization_memberships SET role = 'ADMIN', updated_at = now()
                WHERE organization_id = ? AND user_id = ?
                """, target, ADMIN_ID);
        mockMvc.perform(put("/api/resources/{id}", resource).with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(resourceBody("server", target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(target));
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, ?, 'Test', '{noop}unused-password', now(), now())
                """, id, email);
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES (?, now(), now()) RETURNING id
                """, Long.class, name);
    }

    private void insertMembership(long organizationId, long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, organizationId, userId, role);
    }

    private long insertResource(String name, long organizationId) {
        long resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources
                    (name, type, status, organization_id, config, created_at, updated_at)
                VALUES (?, 'SERVER', 'ACTIVE', ?, '{"host":"server.internal"}', now(), now()) RETURNING id
                """, Long.class, name, organizationId);
        jdbcTemplate.update(
                "INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UNKNOWN')",
                resourceId
        );
        return resourceId;
    }

    private String resourceBody(String name, long organizationId) {
        return """
                {"name":"%s","type":"SERVER","status":"ACTIVE","organizationId":%d,
                 "config":{"host":"server.internal"}}
                """.formatted(name, organizationId);
    }

    private RequestPostProcessor as(long userId) {
        return jwt().jwt(token -> token.subject(Long.toString(userId)));
    }
}
