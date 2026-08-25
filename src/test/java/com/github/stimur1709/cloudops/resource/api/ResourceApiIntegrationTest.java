package com.github.stimur1709.cloudops.resource.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResourceApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        jdbcTemplate.execute("TRUNCATE TABLE resources RESTART IDENTITY");
    }

    @Test
    void applicationStartsAndLiquibaseCreatesResourcesTable() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = 'resources'
                """, Integer.class);
        String identityGeneration = jdbcTemplate.queryForObject("""
                SELECT identity_generation
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'resources'
                  AND column_name = 'id'
                """, String.class);

        org.assertj.core.api.Assertions.assertThat(tableCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(identityGeneration).isEqualTo("BY DEFAULT");
    }

    @Test
    void createsResourceAndReturnsGeneratedIdAndLocation() throws Exception {
        String response = createResource("router-01", "NETWORK_DEVICE", "ACTIVE")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/resources/\\d+")))
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.name").value("router-01"))
                .andExpect(jsonPath("$.type").value("NETWORK_DEVICE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.id");
        org.assertj.core.api.Assertions.assertThat(id.longValue()).isPositive();
    }

    @Test
    void returnsCreatedResourceById() throws Exception {
        String response = createResource("db-01", "DATABASE", "INACTIVE")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/resources/{id}", id.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.longValue()))
                .andExpect(jsonPath("$.name").value("db-01"))
                .andExpect(jsonPath("$.type").value("DATABASE"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void returnsAllValidationErrorsWithTheirFieldNames() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  ",
                                  "type": null,
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "type", "status")))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Name must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'type')].message").value("Type is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].message").value("Status is required"));
    }

    @Test
    void rejectsResourceNameLongerThanOneHundredCharacters() throws Exception {
        createResource("a".repeat(101), "SERVER", "ACTIVE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name must be at most 100 characters"));
    }

    @Test
    void returnsFieldErrorForUnknownEnumValue() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "router-01",
                                  "type": "ROUTER",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        "Type must be one of: NETWORK_DEVICE, SERVER, DATABASE, OTHER"
                ));
    }

    @Test
    void returnsUnifiedErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is invalid"))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void returnsUnifiedNotFoundError() throws Exception {
        mockMvc.perform(get("/api/resources/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/api/resources/999999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions createResource(
            String name,
            String type,
            String status
    ) throws Exception {
        return mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "%s",
                          "type": "%s",
                          "status": "%s"
                        }
                        """.formatted(name, type, status)));
    }
}
