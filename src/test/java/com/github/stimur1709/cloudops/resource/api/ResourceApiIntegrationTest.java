package com.github.stimur1709.cloudops.resource.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.common.api.error.ApiFieldError;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@Import({TestcontainersConfiguration.class, ResourceApiIntegrationTest.ErrorTestController.class})
@SpringBootTest
class ResourceApiIntegrationTest {

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
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
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
                .andExpect(jsonPath("$.healthStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.organizationId").value(organizationId))
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
                .andExpect(jsonPath("$.healthStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.organizationId").value(organizationId))
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
                .andExpect(jsonPath("$.errors", hasSize(5)))
                .andExpect(jsonPath(
                        "$.errors[*].field", containsInAnyOrder("name", "type", "status", "organizationId", "config")))
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
                                  "status": "ACTIVE",
                                  "config": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Type must be one of: NETWORK_DEVICE, SERVER, DATABASE, SERVICE, OTHER"));
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
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Entity not found"))
                .andExpect(jsonPath("$.path").value("/api/resources/999999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void returnsBadRequestForNonNumericResourceId() throws Exception {
        mockMvc.perform(get("/api/resources/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/resources/abc"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("Id must be a number"));
    }

    @Test
    void returnsNumericMessageForAnotherNumericParameter() throws Exception {
        mockMvc.perform(get("/api/test/count/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].message").value("Count must be a number"));
    }

    @Test
    void returnsGenericMessageForNonNumericTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/test/modes/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("mode"))
                .andExpect(jsonPath("$.errors[0].message").value("Mode has an invalid format"));
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(patch("/api/resources/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("HTTP method is not supported for this endpoint"))
                .andExpect(jsonPath("$.path").value("/api/resources/1"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().string("Allow", containsString("PUT")))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void returnsUnsupportedMediaTypeForNonJsonRequest() throws Exception {
        mockMvc.perform(post("/api/resources").contentType(MediaType.TEXT_PLAIN).content("resource"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Content-Type is not supported"))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().string("Accept", containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void returnsFullFieldPathAndEnumNamesForNestedInvalidEnum() throws Exception {
        mockMvc.perform(post("/api/test/nested-enum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configuration": {
                                    "type": "INVALID"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("configuration.type"))
                .andExpect(jsonPath("$.errors[0].message").value("Configuration.type must be one of: FIRST, SECOND"));
    }

    @Test
    void omitsNullFieldFromSerializedFieldError() throws Exception {
        mockMvc.perform(get("/api/test/global-field-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invalid value"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownEndpoint() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Endpoint not found"))
                .andExpect(jsonPath("$.path").value("/api/unknown"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void returnsSafeInternalErrorForUnexpectedException() throws Exception {
        mockMvc.perform(get("/api/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/test/unexpected-error"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(content().string(not(containsString("sensitive internal detail"))));
    }

    private org.springframework.test.web.servlet.ResultActions createResource(String name, String type, String status)
            throws Exception {
        return mockMvc.perform(
                post("/api/resources").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "name": "%s",
                          "type": "%s",
                          "status": "%s",
                          "organizationId": %d,
                          "config": %s
                        }
                        """.formatted(
                                name, type, status, organizationId, configFor(type))));
    }

    private String configFor(String type) {
        return switch (type) {
            case "NETWORK_DEVICE" -> "{\"host\":\"10.0.0.1\"}";
            case "SERVER" -> "{\"host\":\"10.0.0.15\"}";
            case "DATABASE" -> "{\"host\":\"db.internal\",\"port\":5432,\"database\":\"orders\"}";
            case "SERVICE" -> "{\"url\":\"https://api.example.com\"}";
            default -> "{}";
        };
    }

    @RestController
    static class ErrorTestController {

        @GetMapping("/api/test/unexpected-error")
        void fail() {
            throw new IllegalStateException("sensitive internal detail");
        }

        @GetMapping("/api/test/count/{count}")
        void count(@PathVariable long count) {}

        @GetMapping("/api/test/modes/{mode}")
        void mode(@PathVariable TestMode mode) {}

        @PostMapping("/api/test/nested-enum")
        void nestedEnum(@RequestBody NestedEnumRequest request) {}

        @GetMapping("/api/test/global-field-error")
        ApiFieldError globalFieldError() {
            return new ApiFieldError(null, "Invalid value");
        }
    }

    record NestedEnumRequest(NestedConfiguration configuration) {}

    record NestedConfiguration(TestMode type) {}

    enum TestMode {
        FIRST,
        SECOND;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
