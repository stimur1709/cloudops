package com.github.stimur1709.cloudops.membership.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.membership.application.OrganizationMembershipService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class OrganizationMembershipApiIntegrationTest {

    private static final long ADMIN_ID = 10_001L;
    private static final long MEMBER_ID = 10_002L;
    private static final long OTHER_ID = 10_003L;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrganizationMembershipService membershipService;

    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        insertUser(TestAuthentication.USER_ID, "owner@example.com");
        insertUser(ADMIN_ID, "admin@example.com");
        insertUser(MEMBER_ID, "member@example.com");
        insertUser(OTHER_ID, "other@example.com");
        organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Platform', now(), now()) RETURNING id
                """, Long.class);
        insertMembership(TestAuthentication.USER_ID, "OWNER");
        insertMembership(ADMIN_ID, "ADMIN");
        insertMembership(MEMBER_ID, "MEMBER");
    }

    @Test
    void memberCanListMembersAndServerScopeCannotBeBypassed() throws Exception {
        mockMvc.perform(post("/api/organizations/{id}/members/search", organizationId)
                        .with(as(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"start":0,"size":10,"filter":{"operator":"OR","conditions":[
                                  {"field":"organizationId","operation":"EQ","value":"999999"},
                                  {"field":"role","operation":"EQ","value":"OWNER"}
                                ]},"sort":[{"field":"userId","order":"ASC"}],"getTotal":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].userId").value(TestAuthentication.USER_ID))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void ownerCanAddChangeAndRemoveMembers() throws Exception {
        add(OTHER_ID, "ADMIN", TestAuthentication.USER_ID).andExpect(status().isCreated());
        mockMvc.perform(put("/api/organizations/{id}/members/{userId}", organizationId, OTHER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mockMvc.perform(delete("/api/organizations/{id}/members/{userId}", organizationId, OTHER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanManageOnlyMembers() throws Exception {
        add(OTHER_ID, "MEMBER", ADMIN_ID).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/organizations/{id}/members/{userId}", organizationId, OTHER_ID)
                        .with(as(ADMIN_ID)))
                .andExpect(status().isNoContent());

        add(OTHER_ID, "ADMIN", ADMIN_ID).andExpect(status().isForbidden());
        add(OTHER_ID, "OWNER", ADMIN_ID).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/organizations/{id}/members/{userId}", organizationId,
                        TestAuthentication.USER_ID).with(as(ADMIN_ID)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizations/{id}/members/{userId}", organizationId, MEMBER_ID)
                        .with(as(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotChangeCompositionOrPromoteSelf() throws Exception {
        add(OTHER_ID, "MEMBER", MEMBER_ID).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizations/{id}/members/{userId}", organizationId, MEMBER_ID)
                        .with(as(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/organizations/{id}/members/{userId}", organizationId, ADMIN_ID)
                        .with(as(MEMBER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerCannotBeDemotedOrRemoved() throws Exception {
        mockMvc.perform(put("/api/organizations/{id}/members/{userId}", organizationId,
                        TestAuthentication.USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_REQUIRED"));
        mockMvc.perform(delete("/api/organizations/{id}/members/{userId}", organizationId,
                        TestAuthentication.USER_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void nonMemberCannotDiscoverOrganizationThroughMembersApi() throws Exception {
        mockMvc.perform(post("/api/organizations/{id}/members/search", organizationId)
                        .with(as(OTHER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":0,\"size\":10,\"getTotal\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void concurrentOwnerRemovalsPreserveAtLeastOneOwner() throws Exception {
        jdbcTemplate.update("""
                UPDATE organization_memberships SET role = 'OWNER', updated_at = now()
                WHERE organization_id = ? AND user_id = ?
                """, organizationId, ADMIN_ID);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> removeAfter(start, ADMIN_ID, TestAuthentication.USER_ID));
            var second = executor.submit(() -> removeAfter(start, TestAuthentication.USER_ID, ADMIN_ID));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            org.assertj.core.api.Assertions.assertThat(successes).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM organization_memberships
                    WHERE organization_id = ? AND role = 'OWNER'
                    """, Integer.class, organizationId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions add(long userId, String role, long actorId)
            throws Exception {
        return mockMvc.perform(post("/api/organizations/{id}/members", organizationId)
                .with(as(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d,\"role\":\"%s\"}".formatted(userId, role)));
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, ?, 'Test', '{noop}unused-password', now(), now())
                """, id, email);
    }

    private void insertMembership(long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, organizationId, userId, role);
    }

    private RequestPostProcessor as(long userId) {
        return jwt().jwt(token -> token.subject(Long.toString(userId)));
    }

    private boolean removeAfter(CountDownLatch start, long targetUserId, long actorUserId) {
        try {
            start.await();
            membershipService.remove(organizationId, targetUserId, actorUserId);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
