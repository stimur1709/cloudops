package com.github.stimur1709.cloudops.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        jdbcTemplate.execute("""
                TRUNCATE TABLE organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
    }

    @Test
    void createsGetsUpdatesAndNormalizesUser() throws Exception {
        String created = create("  Alice@Example.COM  ", "Alice")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        Instant oldUpdatedAt = Instant.parse(JsonPath.read(created, "$.updatedAt"));

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice"));

        String updated = mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"NEW@EXAMPLE.COM", "displayName":"Alice Smith"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice Smith"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.read(updated, "$.updatedAt"))).isAfter(oldUpdatedAt);
    }

    @Test
    void rejectsDuplicateNormalizedEmailOnCreateAndUpdate() throws Exception {
        create("alice@example.com", "Alice").andExpect(status().isCreated());
        create(" ALICE@EXAMPLE.COM ", "Other")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_CONFLICT"));
        long second = insertUser("second@example.com", "Second", Instant.now());
        mockMvc.perform(put("/api/users/{id}", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Alice@Example.com", "displayName":"Second"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_CONFLICT"));
    }

    @Test
    void validatesAndReturnsNotFound() throws Exception {
        create("invalid", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(delete("/api/users/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchesFiltersSortsAndControlsTotal() throws Exception {
        insertUser("zulu@example.com", "Zulu", Instant.parse("2026-08-20T00:00:00Z"));
        insertUser("alice@example.com", "Alice Team", Instant.parse("2026-08-21T00:00:00Z"));
        insertUser("bob@example.com", "Bob Team", Instant.parse("2026-08-22T00:00:00Z"));

        search("""
                {"start":0,"size":10,"filter":{"operator":"AND","conditions":[
                  {"field":"displayName","operation":"CONTAINS","value":"Team"}
                ]},"sort":[{"field":"email","order":"DESC"}],"getTotal":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email", contains("bob@example.com", "alice@example.com")))
                .andExpect(jsonPath("$.total").value(2));

        search("{\"start\":1,\"size\":1,\"getTotal\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void keepsUserWhitelistSeparate() throws Exception {
        search("""
                {"start":0,"size":10,"filter":{"operator":"AND","conditions":[
                  {"field":"role","operation":"EQ","value":"OWNER"}
                ]},"getTotal":false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].field"));
    }

    @Test
    void deletesUserWithoutMembership() throws Exception {
        long id = insertUser("delete@example.com", "Delete", Instant.now());
        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, id))
                .isZero();
    }

    private ResultActions create(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"displayName\":\"%s\"}".formatted(email, displayName)));
    }

    private ResultActions search(String body) throws Exception {
        return mockMvc.perform(post("/api/users/search")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long insertUser(String email, String displayName, Instant instant) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, email, displayName, Timestamp.from(instant), Timestamp.from(instant));
    }
}
