package com.github.stimur1709.cloudops.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
class OpenApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JsonMapper jsonMapper;

    private MockMvc mvc;
    private JsonNode document;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        document = jsonMapper.readTree(json);
        Files.writeString(Path.of("target/openapi.json"), json);
    }

    @Test
    void generatesPublicDocumentationAndSwaggerUi() throws Exception {
        assertThat(document.path("openapi").asText()).startsWith("3.0.");
        assertThat(document.at("/info/title").asText()).isEqualTo("CloudOps API");
        assertThat(document.at("/info/version").asText()).isEqualTo("0.0.1-SNAPSHOT");
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        assertThat(document.path("paths").propertyNames())
                .contains(
                        "/api/auth/login",
                        "/api/auth/me",
                        "/api/users/search",
                        "/api/organizations/search",
                        "/api/organizations/{organizationId}/members/search",
                        "/api/resources/search",
                        "/api/credentials/search",
                        "/api/organizations/{organizationId}/monitoring-settings",
                        "/api/resources/{resourceId}/monitors",
                        "/api/monitors/{id}/results/search",
                        "/api/resources/{resourceId}/health/availability",
                        "/api/resources/{resourceId}/health/events/search",
                        "/api/resources/{resourceId}/tasks",
                        "/api/tasks/search",
                        "/api/resources/{resourceId}/task-capabilities");
        assertReferencesResolve(document);
    }

    @Test
    void describesAuthenticationAndSharedErrors() {
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText())
                .isEqualTo("bearer");
        assertThat(document.at("/components/securitySchemes/bearerAuth/bearerFormat")
                        .asText())
                .isEqualTo("JWT");
        for (String path :
                new String[] {"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"}) {
            assertThat(operation(path, "post").path("security").isEmpty()).isTrue();
        }
        for (String path : new String[] {"/api/resources/{id}", "/api/tasks/{id}", "/api/auth/me"}) {
            assertThat(operation(path, "get").at("/security/0/bearerAuth").isArray())
                    .isTrue();
            for (String code : new String[] {"400", "401", "403", "404", "409"}) {
                assertThat(operation(path, "get")
                                .at("/responses/" + code + "/$ref")
                                .asText())
                        .isEqualTo("#/components/responses/Error" + code);
            }
        }
        assertThat(schema("ApiError").at("/properties/errors/items/$ref").asText())
                .isEqualTo("#/components/schemas/ApiFieldError");
        assertThat(schema("ApiError").at("/properties/timestamp/format").asText())
                .isEqualTo("date-time");
        assertThat(schema("ApiError").path("required").toString())
                .contains("code", "message", "timestamp", "path", "errors");
        assertThat(schema("SearchRequest").at("/properties/filter/$ref").asText())
                .isEqualTo("#/components/schemas/Filter");
        assertThat(schema("Condition").at("/properties/operation/enum").toString())
                .contains("EQ", "NE", "CONTAINS", "GT", "GE", "LT", "LE");
        assertThat(schema("SearchResponseResourceResponse")
                        .at("/properties/items/items/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ResourceResponse");
        assertThat(operation("/api/resources/search", "post")
                        .path("description")
                        .asText())
                .contains(
                        "healthStatus [EQ, NE]",
                        "name [CONTAINS, EQ, NE]",
                        "Sortable fields: createdAt, id, name, organizationId, status, type, updatedAt",
                        "Default sort: id ASC")
                .doesNotContain("ResourceEntity", "resource.name");
        assertThat(operation("/api/auth/login", "post")
                        .at("/responses/200/headers/Set-Cookie/description")
                        .asText())
                .contains("HttpOnly", "cloudops_refresh");
        assertThat(operation("/api/auth/refresh", "post").at("/parameters/0/in").asText())
                .isEqualTo("cookie");
        assertThat(operation("/api/auth/logout", "post")
                        .at("/responses/204/headers/Set-Cookie")
                        .isObject())
                .isTrue();
    }

    @Test
    void preservesTypedTaskAndResourceContracts() {
        assertThat(schema("CreateTaskRequest").at("/properties/parameters/$ref").asText())
                .isEqualTo("#/components/schemas/RunCommandParameters");
        assertThat(schema("RunCommandParameters").at("/properties/command/type").asText())
                .isEqualTo("string");
        assertThat(schema("CreateTaskRequest").path("required").toString()).contains("type", "parameters");
        assertThat(schema("TaskResponse").at("/properties/result/nullable").asBoolean())
                .isTrue();
        assertThat(schema("RunCommandResult").path("properties").propertyNames())
                .contains("exitCode", "stdout", "stderr", "durationMs", "outputTruncated");
        assertThat(schema("CreateResourceRequest").at("/properties/config/$ref").asText())
                .isEqualTo("#/components/schemas/ResourceConfig");
        assertThat(schema("ResourceConfig").path("oneOf").size()).isEqualTo(5);
        assertThat(schema("CredentialRequest").at("/discriminator/propertyName").asText())
                .isEqualTo("type");
        assertThat(schema("CredentialRequest").path("oneOf").size()).isEqualTo(2);
        assertThat(schema("ServerResourceConfig").path("properties").propertyNames())
                .contains("host", "port", "sshPort");
        assertThat(schema("ServerResourceConfig").path("allOf").isMissingNode()).isTrue();
        assertThat(schema("UsernamePasswordCredentialRequest").path("allOf").isMissingNode())
                .isTrue();
        assertThat(schema("UsernamePasswordCredentialRequest").toString()).contains("password");
        assertThat(schema("SshPrivateKeyCredentialRequest").toString()).contains("privateKey");
        assertThat(schema("ProbeExecutionResult").path("oneOf").size()).isEqualTo(2);
        assertThat(schema("Completed").at("/properties/data/oneOf").size()).isEqualTo(6);
        assertThat(schema("MonitoringResultResponse")
                        .at("/properties/result/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ProbeExecutionResult");
    }

    private JsonNode schema(String name) {
        JsonNode schema = document.path("components").path("schemas").path(name);
        assertThat(schema.isObject()).as(name).isTrue();
        return schema;
    }

    private JsonNode operation(String path, String method) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.isObject()).as(method + " " + path).isTrue();
        return operation;
    }

    private void assertReferencesResolve(JsonNode node) {
        if (node.isObject() && node.has("$ref")) {
            String ref = node.path("$ref").asText();
            assertThat(ref).startsWith("#/");
            assertThat(document.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
        }
        node.forEach(this::assertReferencesResolve);
    }
}
