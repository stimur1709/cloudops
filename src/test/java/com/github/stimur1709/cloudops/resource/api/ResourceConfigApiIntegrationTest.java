package com.github.stimur1709.cloudops.resource.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestAuthentication;
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
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResourceConfigApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(applicationContext);
        jdbcTemplate.execute("""
                TRUNCATE TABLE outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'test@example.com', 'Test', '{noop}unused-password', now(), now())
                """, TestAuthentication.USER_ID);
        organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Test', now(), now()) RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO organization_memberships
                    (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, organizationId, TestAuthentication.USER_ID);
    }

    @Test
    void createsAndReturnsEveryTypedConfig() throws Exception {
        create("server", "SERVER", "{\"host\":\"10.0.0.15\",\"port\":22}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.host").value("10.0.0.15"))
                .andExpect(jsonPath("$.config.port").value(22));
        create("network", "NETWORK_DEVICE", "{\"host\":\"10.0.0.1\",\"managementPort\":22}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.managementPort").value(22));
        create("database", "DATABASE", "{\"host\":\"db.internal\",\"port\":5432,\"database\":\"orders\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.database").value("orders"));
        create("service", "SERVICE", "{\"url\":\"https://api.example.com\",\"expectedStatus\":204,\"timeoutMs\":1000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.url").value("https://api.example.com"));
        create("other", "OTHER", "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config").isMap())
                .andExpect(jsonPath("$.config.*", hasSize(0)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resources WHERE jsonb_typeof(config) = 'object'",
                Integer.class
        )).isEqualTo(5);
    }

    @Test
    void persistsJsonbAndReturnsItAfterLoadingResource() throws Exception {
        String response = create("database", "DATABASE", """
                {"host":"db.internal","port":5432,"database":"orders"}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT config ->> 'database' FROM resources WHERE id = ?", String.class, id
        )).isEqualTo("orders");
        mockMvc.perform(get("/api/resources/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.host").value("db.internal"))
                .andExpect(jsonPath("$.config.port").value(5432))
                .andExpect(jsonPath("$.config.database").value("orders"));
    }

    @Test
    void appliesServiceDefaults() throws Exception {
        create("service", "SERVICE", "{\"url\":\"https://api.example.com\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.expectedStatus").value(200))
                .andExpect(jsonPath("$.config.timeoutMs").value(5000));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT (config ->> 'timeoutMs')::integer FROM resources WHERE name = 'service'",
                Integer.class
        )).isEqualTo(5000);
    }

    @Test
    void rejectsInvalidConfigValuesWithNestedFieldPaths() throws Exception {
        create("server", "SERVER", "{\"host\":\"server.internal\",\"port\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.port"));
        create("service-url", "SERVICE", "{\"url\":\"ftp://api.example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.url"))
                .andExpect(jsonPath("$.errors[0].message").value("URL must use http or https"));
        create("service-timeout", "SERVICE", "{\"url\":\"https://api.example.com\",\"timeoutMs\":60001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.timeoutMs"));
        create("database", "DATABASE", "{\"host\":\"db.internal\",\"port\":5432}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.database"));
    }

    @Test
    void rejectsMissingConfigUnknownFieldsAndConfigForAnotherType() throws Exception {
        createWithoutConfig("server", "SERVER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config"));
        create("server-extra", "SERVER", "{\"host\":\"server.internal\",\"password\":\"secret\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.password"))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown field"));
        create("wrong", "SERVER", "{\"url\":\"https://api.example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.url"));
    }

    @Test
    void updatesConfigAndChangesTypeOnlyWithCompatibleConfig() throws Exception {
        String response = create("resource", "SERVER", "{\"host\":\"old.internal\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        update(id, "SERVER", "{\"host\":\"new.internal\",\"port\":2222}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.host").value("new.internal"))
                .andExpect(jsonPath("$.config.port").value(2222));
        update(id, "SERVICE", "{\"url\":\"https://new.example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SERVICE"))
                .andExpect(jsonPath("$.config.expectedStatus").value(200));
        update(id, "DATABASE", "{\"url\":\"https://wrong.example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("config.url"));
    }

    private ResultActions create(String name, String type, String config) throws Exception {
        return mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resourceJson(name, type, config)));
    }

    private ResultActions createWithoutConfig(String name, String type) throws Exception {
        return mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","type":"%s","status":"ACTIVE","organizationId":%d}
                        """.formatted(name, type, organizationId)));
    }

    private ResultActions update(long id, String type, String config) throws Exception {
        return mockMvc.perform(put("/api/resources/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resourceJson("resource", type, config)));
    }

    private String resourceJson(String name, String type, String config) {
        return """
                {"name":"%s","type":"%s","status":"ACTIVE","organizationId":%d,"config":%s}
                """.formatted(name, type, organizationId, config);
    }
}
