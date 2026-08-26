package com.github.stimur1709.cloudops.membership.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrganizationMembershipApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long organizationId;
    private long firstUserId;
    private long secondUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        jdbcTemplate.execute("""
                TRUNCATE TABLE organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        organizationId = insertOrganization("Platform");
        firstUserId = insertUser("first@example.com", "First");
        secondUserId = insertUser("second@example.com", "Second");
    }

    @Test
    void addsListsUpdatesAndRemovesMembersInStableOrder() throws Exception {
        add(firstUserId, "OWNER").andExpect(status().isCreated());
        add(secondUserId, "MEMBER").andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .param("start", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].userId", contains((int) firstUserId, (int) secondUserId)));

        mockMvc.perform(put("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, secondUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, secondUserId))
                .andExpect(status().isNoContent());
    }

    @Test
    void appliesOffsetAndValidatesPageSize() throws Exception {
        add(firstUserId, "OWNER").andExpect(status().isCreated());
        add(secondUserId, "MEMBER").andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .param("start", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(secondUserId));
        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownOrganizationAndUserAndDuplicateMembership() throws Exception {
        mockMvc.perform(post("/api/organizations/999999/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"role\":\"OWNER\"}".formatted(firstUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999999,\"role\":\"OWNER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        add(firstUserId, "OWNER").andExpect(status().isCreated());
        add(firstUserId, "OWNER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_CONFLICT"));
    }

    @Test
    void requiresFirstMemberAndLastRemainingOwnerToBeOwner() throws Exception {
        add(firstUserId, "MEMBER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_REQUIRED"));
        add(firstUserId, "OWNER").andExpect(status().isCreated());

        mockMvc.perform(put("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, firstUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_REQUIRED"));
        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, firstUserId))
                .andExpect(status().isConflict());
    }

    @Test
    void permitsDemotingAndRemovingOneOfMultipleOwners() throws Exception {
        add(firstUserId, "OWNER").andExpect(status().isCreated());
        add(secondUserId, "OWNER").andExpect(status().isCreated());

        mockMvc.perform(put("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, firstUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, firstUserId))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsMissingMembership() throws Exception {
        add(firstUserId, "OWNER").andExpect(status().isCreated());
        mockMvc.perform(put("/api/organizations/{organizationId}/members/{userId}",
                        organizationId, secondUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void preventsDeletingOrganizationOrUserWithMembership() throws Exception {
        add(firstUserId, "OWNER").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/{id}", firstUserId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_IN_USE"));
        mockMvc.perform(delete("/api/organizations/{id}", organizationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_IN_USE"));
    }

    @Test
    void databaseEnforcesMembershipConstraintsAndIndexes() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM databasechangelog
                WHERE id = '002-create-users-and-memberships'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'organization_memberships'
                  AND indexname IN ('organization_memberships_organization_idx',
                                    'organization_memberships_user_idx')
                """, Integer.class)).isEqualTo(2);

        insertMembership(organizationId, firstUserId, "OWNER");
        assertThatThrownBy(() -> insertMembership(organizationId, firstUserId, "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMembership(organizationId, 999999, "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, NULL, now(), now())
                """, organizationId, secondUserId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions add(long userId, String role) throws Exception {
        return mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d,\"role\":\"%s\"}".formatted(userId, role)));
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES (?, now(), now()) RETURNING id
                """, Long.class, name);
    }

    private long insertUser(String email, String displayName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, display_name, created_at, updated_at)
                VALUES (?, ?, now(), now()) RETURNING id
                """, Long.class, email, displayName);
    }

    private void insertMembership(long organization, long user, String role) {
        jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, organization, user, role);
    }
}
