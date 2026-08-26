package com.github.stimur1709.cloudops.organization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.TestAuthentication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrganizationApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'test@example.com', 'Test', '{noop}unused-password', now(), now())
                """, TestAuthentication.USER_ID);
    }

    @Test
    void liquibaseCreatesCompleteSchemaAndConstraints() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM databasechangelog
                WHERE id = '001-create-initial-schema'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'resources'
                  AND column_name = 'organization_id'
                """, String.class)).isEqualTo("NO");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = current_schema()
                  AND constraint_name = 'resources_organization_fk'
                """, String.class)).isEqualTo("RESTRICT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_schema = current_schema()
                  AND constraint_name = 'resources_organization_name_key'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO resources (name, type, status, created_at, updated_at)
                VALUES ('missing-organization', 'SERVER', 'ACTIVE', now(), now())
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO resources (name, type, status, organization_id, created_at, updated_at)
                VALUES ('unknown-organization', 'SERVER', 'ACTIVE', 999999, now(), now())
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void createsGetsAndUpdatesOrganization() throws Exception {
        String created = createOrganization("Platform")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/organizations/1"))
                .andExpect(jsonPath("$.name").value("Platform"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get("/api/organizations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"));

        String updated = mockMvc.perform(put("/api/organizations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Core Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Core Platform"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.read(updated, "$.updatedAt"))).isNotNull();
    }

    @Test
    void validatesOrganizationAndReturnsNotFound() throws Exception {
        createOrganization(" ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(get("/api/organizations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
        mockMvc.perform(put("/api/organizations/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesFiltersSortsAndControlsTotal() throws Exception {
        insertOrganization("Zulu", Instant.parse("2026-08-20T00:00:00Z"));
        insertOrganization("Alpha Platform", Instant.parse("2026-08-21T00:00:00Z"));
        insertOrganization("Beta Platform", Instant.parse("2026-08-22T00:00:00Z"));

        search("""
                {"start":0,"size":10,"filter":{"operator":"AND","conditions":[
                  {"field":"name","operation":"CONTAINS","value":"Platform"}
                ]},"sort":[{"field":"name","order":"DESC"}],"getTotal":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Beta Platform", "Alpha Platform")))
                .andExpect(jsonPath("$.total").value(2));

        search("{\"start\":0,\"size\":1,\"getTotal\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void keepsStablePagesWhenSortValuesAreEqual() throws Exception {
        long first = insertOrganization("Same", Instant.now());
        long second = insertOrganization("Same", Instant.now());
        long third = insertOrganization("Same", Instant.now());

        search("{\"start\":0,\"size\":2,\"sort\":[{\"field\":\"name\",\"order\":\"ASC\"}],\"getTotal\":false}")
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) second)));
        search("{\"start\":2,\"size\":2,\"sort\":[{\"field\":\"name\",\"order\":\"ASC\"}],\"getTotal\":false}")
                .andExpect(jsonPath("$.items[*].id", contains((int) third)));
    }

    @Test
    void enforcesSeparateSearchWhitelistsAndRejectsNullItems() throws Exception {
        search(filter("type", "EQ", "SERVER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].field"));
        search(filter("name", "GT", "A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].operation"));
        search("{\"start\":0,\"size\":10,\"filter\":{\"operator\":\"AND\",\"conditions\":[null]},\"getTotal\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        search("{\"start\":0,\"size\":10,\"sort\":[null],\"getTotal\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsFiltersAndMovesResourceBetweenOrganizations() throws Exception {
        long first = insertOrganization("First", Instant.now());
        long second = insertOrganization("Second", Instant.now());
        String resource = createResource("router", first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(first))
                .andReturn().getResponse().getContentAsString();
        long resourceId = ((Number) JsonPath.read(resource, "$.id")).longValue();

        mockMvc.perform(post("/api/resources/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filter("organizationId", "EQ", Long.toString(first))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(put("/api/resources/{id}", resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("router", second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(second));
    }

    @Test
    void rejectsUnknownOrganizationForResourceCreateAndUpdate() throws Exception {
        long organization = insertOrganization("Existing", Instant.now());
        createResource("router", 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
        long resource = insertResource("server", organization);
        mockMvc.perform(put("/api/resources/{id}", resource)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("server", 999999)))
                .andExpect(status().isNotFound());
    }

    @Test
    void scopesResourceNameUniquenessToOrganization() throws Exception {
        long first = insertOrganization("First", Instant.now());
        long second = insertOrganization("Second", Instant.now());
        createResource("shared", first).andExpect(status().isCreated());
        createResource("shared", second).andExpect(status().isCreated());
        createResource("shared", first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_NAME_CONFLICT"));
        long other = insertResource("other", first);
        mockMvc.perform(put("/api/resources/{id}", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody("shared", first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_NAME_CONFLICT"));
    }

    @Test
    void preventsDeletingOrganizationWithResourcesOrMemberships() throws Exception {
        long used = insertOrganization("Used", Instant.now());
        long empty = insertOrganization("Empty", Instant.now());
        insertResource("server", used);

        mockMvc.perform(delete("/api/organizations/{id}", used))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_IN_USE"));
        mockMvc.perform(delete("/api/organizations/{id}", empty))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_IN_USE"));
    }

    private ResultActions createOrganization(String name) throws Exception {
        return mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)));
    }

    private ResultActions createResource(String name, long organizationId) throws Exception {
        return mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resourceBody(name, organizationId)));
    }

    private ResultActions search(String body) throws Exception {
        return mockMvc.perform(post("/api/organizations/search")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String filter(String field, String operation, String value) {
        return """
                {"start":0,"size":10,"filter":{"operator":"AND","conditions":[
                  {"field":"%s","operation":"%s","value":"%s"}
                ]},"getTotal":false}
                """.formatted(field, operation, value);
    }

    private String resourceBody(String name, long organizationId) {
        return """
                {"name":"%s","type":"SERVER","status":"ACTIVE","organizationId":%d}
                """.formatted(name, organizationId);
    }

    private long insertOrganization(String name, Instant instant) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, name, Timestamp.from(instant), Timestamp.from(instant));
        jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, id, TestAuthentication.USER_ID);
        return id;
    }

    private long insertResource(String name, long organizationId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, created_at, updated_at)
                VALUES (?, 'SERVER', 'ACTIVE', ?, now(), now()) RETURNING id
                """, Long.class, name, organizationId);
    }
}
