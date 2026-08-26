package com.github.stimur1709.cloudops.resource.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import com.github.stimur1709.cloudops.SqlStatementRecorder;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class ResourceSearchApiIntegrationTest {

    private static final Instant FIRST_CREATED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant SECOND_CREATED_AT = Instant.parse("2026-08-21T01:00:00Z");
    private static final Instant THIRD_CREATED_AT = Instant.parse("2026-08-22T01:00:00Z");

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlStatementRecorder sqlStatementRecorder;

    private long organizationId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        jdbcTemplate.execute("""
                TRUNCATE TABLE organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        organizationId = insertOrganization("Test organization");
        sqlStatementRecorder.clear();
    }

    @Test
    void returnsResourcesWithoutFilterInStableIdOrder() throws Exception {
        insertResource("first", "SERVER", "ACTIVE", FIRST_CREATED_AT);
        insertResource("second", "DATABASE", "INACTIVE", SECOND_CREATED_AT);

        search("""
                {
                  "start": 0,
                  "size": 20,
                  "getTotal": false
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].name", contains("first", "second")))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void appliesStartAndSize() throws Exception {
        insertThreeResources();

        search("""
                {
                  "start": 1,
                  "size": 1,
                  "getTotal": false
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("second"));
    }

    @Test
    void filtersByOneCondition() throws Exception {
        insertThreeResources();

        search(filterRequest("AND", """
                {"field": "type", "operation": "EQ", "value": "SERVER"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("first", "third")));
    }

    @Test
    void combinesConditionsWithAnd() throws Exception {
        insertThreeResources();

        search(filterRequest("AND", """
                {"field": "type", "operation": "EQ", "value": "SERVER"},
                {"field": "status", "operation": "EQ", "value": "INACTIVE"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("third"));
    }

    @Test
    void combinesConditionsWithOr() throws Exception {
        insertThreeResources();

        search(filterRequest("OR", """
                {"field": "type", "operation": "EQ", "value": "DATABASE"},
                {"field": "status", "operation": "EQ", "value": "INACTIVE"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("second", "third")));
    }

    @Test
    void filtersNameWithContains() throws Exception {
        insertResource("prod-server-01", "SERVER", "ACTIVE", FIRST_CREATED_AT);
        insertResource("test-server-01", "SERVER", "ACTIVE", SECOND_CREATED_AT);

        search(filterRequest("AND", """
                {"field": "name", "operation": "CONTAINS", "value": "prod"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("prod-server-01"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
            NE;2;first|third
            GT;1;second|third
            GE;2;second|third
            LT;3;first|second
            LE;2;first|second
            """)
    void supportsComparisonOperations(String operation, String value, String expectedNames) throws Exception {
        insertThreeResources();

        String request = filterRequest("AND", """
                {"field": "id", "operation": "%s", "value": "%s"}
                """.formatted(operation, value));

        search(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains(expectedNames.split("\\|"))));
    }

    @Test
    void sortsByOneField() throws Exception {
        insertThreeResources();

        search(sortRequest("""
                {"field": "name", "order": "DESC"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("third", "second", "first")));
    }

    @Test
    void sortsByMultipleFieldsInRequestOrder() throws Exception {
        insertThreeResources();

        search(sortRequest("""
                {"field": "status", "order": "ASC"},
                {"field": "name", "order": "DESC"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("first", "third", "second")));
    }

    @Test
    void returnsTotalAndExecutesCountWhenRequested() throws Exception {
        insertThreeResources();
        sqlStatementRecorder.clear();

        search("""
                {
                  "start": 0,
                  "size": 1,
                  "getTotal": true
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").value(3));

        assertThat(resourceSelectStatements()).hasSize(2);
        assertThat(countStatements()).hasSize(1);
    }

    @Test
    void omitsTotalAndDoesNotExecuteCountWhenNotRequested() throws Exception {
        insertThreeResources();
        sqlStatementRecorder.clear();

        search("""
                {
                  "start": 0,
                  "size": 1,
                  "getTotal": false
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").doesNotExist());

        assertThat(resourceSelectStatements()).hasSize(1);
        assertThat(countStatements()).isEmpty();
    }

    @Test
    void rejectsUnknownFilterField() throws Exception {
        search(filterRequest("AND", """
                {"field": "secret", "operation": "EQ", "value": "value"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Search request is invalid"))
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].field"))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown filter field: secret"));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        search(sortRequest("""
                {"field": "secret", "order": "ASC"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("sort[0].field"))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown sort field: secret"));
    }

    @Test
    void rejectsOperationUnsupportedByFieldType() throws Exception {
        search(filterRequest("AND", """
                {"field": "id", "operation": "CONTAINS", "value": "1"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].operation"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        "Operation CONTAINS is not supported for field id"
                ));
    }

    @Test
    void rejectsValuesThatCannotBeConvertedToFieldTypes() throws Exception {
        assertInvalidFilterValue("id", "EQ", "abc", "Value must be a valid integer number");
        assertInvalidFilterValue("type", "EQ", "ROUTER", "Value must be one of: NETWORK_DEVICE, SERVER, DATABASE, OTHER");
        assertInvalidFilterValue("createdAt", "GT", "yesterday", "Value must be a valid ISO-8601 instant");
    }

    @Test
    void validatesSearchWindow() throws Exception {
        search("""
                {
                  "start": -1,
                  "size": 101,
                  "getTotal": false
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("start", "size")));
    }

    @Test
    void updatesResourceAndChangesUpdatedAt() throws Exception {
        long id = insertResource("old-name", "SERVER", "ACTIVE", FIRST_CREATED_AT);
        Instant previousUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM resources WHERE id = ?",
                Timestamp.class,
                id
        ).toInstant();

        String response = mockMvc.perform(put("/api/resources/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "router-core-01",
                                  "type": "NETWORK_DEVICE",
                                  "status": "INACTIVE"
                                  ,"organizationId": %d
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("router-core-01"))
                .andExpect(jsonPath("$.type").value("NETWORK_DEVICE"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Instant updatedAt = Instant.parse(JsonPath.read(response, "$.updatedAt"));
        assertThat(updatedAt).isAfter(previousUpdatedAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM resources WHERE id = ?",
                String.class,
                id
        )).isEqualTo("router-core-01");
    }

    @Test
    void appliesCreateValidationRulesWhenUpdating() throws Exception {
        long id = insertResource("old-name", "SERVER", "ACTIVE", FIRST_CREATED_AT);

        mockMvc.perform(put("/api/resources/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  ",
                                  "type": null,
                                  "status": null
                                  ,"organizationId": %d
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "type", "status")));
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownResource() throws Exception {
        mockMvc.perform(put("/api/resources/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "router-core-01",
                                  "type": "NETWORK_DEVICE",
                                  "status": "INACTIVE"
                                  ,"organizationId": %d
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deletesExistingResource() throws Exception {
        long id = insertResource("obsolete", "OTHER", "INACTIVE", FIRST_CREATED_AT);

        mockMvc.perform(delete("/api/resources/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resources WHERE id = ?",
                Long.class,
                id
        )).isZero();
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownResource() throws Exception {
        mockMvc.perform(delete("/api/resources/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private void insertThreeResources() {
        insertResource("first", "SERVER", "ACTIVE", FIRST_CREATED_AT);
        insertResource("second", "DATABASE", "INACTIVE", SECOND_CREATED_AT);
        insertResource("third", "SERVER", "INACTIVE", THIRD_CREATED_AT);
    }

    private long insertResource(String name, String type, String status, Instant createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, name, type, status, organizationId,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private long insertOrganization(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES (?, now(), now()) RETURNING id
                """, Long.class, name);
    }

    private ResultActions search(String request) throws Exception {
        return mockMvc.perform(post("/api/resources/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private String filterRequest(String operator, String conditions) {
        return """
                {
                  "start": 0,
                  "size": 20,
                  "filter": {
                    "operator": "%s",
                    "conditions": [
                      %s
                    ]
                  },
                  "getTotal": false
                }
                """.formatted(operator, conditions);
    }

    private String sortRequest(String sort) {
        return """
                {
                  "start": 0,
                  "size": 20,
                  "sort": [
                    %s
                  ],
                  "getTotal": false
                }
                """.formatted(sort);
    }

    private void assertInvalidFilterValue(
            String field,
            String operation,
            String value,
            String expectedMessage
    ) throws Exception {
        search(filterRequest("AND", """
                {"field": "%s", "operation": "%s", "value": "%s"}
                """.formatted(field, operation, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("filter.conditions[0].value"))
                .andExpect(jsonPath("$.errors[0].message").value(expectedMessage));
    }

    private java.util.List<String> resourceSelectStatements() {
        return sqlStatementRecorder.statements().stream()
                .filter(this::isResourceSelect)
                .toList();
    }

    private java.util.List<String> countStatements() {
        return resourceSelectStatements().stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains("select count("))
                .toList();
    }

    private boolean isResourceSelect(String sql) {
        String normalized = Arrays.stream(sql.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(part -> !part.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return normalized.startsWith("select ") && normalized.contains(" from resources ");
    }
}
