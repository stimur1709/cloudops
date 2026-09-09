package com.github.stimur1709.cloudops.openapi;

import com.github.stimur1709.cloudops.auth.config.RefreshTokenProperties;
import com.github.stimur1709.cloudops.common.api.error.ApiError;
import com.github.stimur1709.cloudops.common.config.SecurityConfiguration;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.credential.persistence.CredentialSearchDefinition;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipSearchDefinition;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultSearchDefinition;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventSearchDefinition;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationSearchDefinition;
import com.github.stimur1709.cloudops.resource.persistence.ResourceSearchDefinition;
import com.github.stimur1709.cloudops.task.persistence.TaskSearchDefinition;
import com.github.stimur1709.cloudops.user.persistence.UserSearchDefinition;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {

    private static final Map<String, String> ERRORS = Map.of(
            "400", "Validation failed or malformed request",
            "401", "Authentication or refresh session is missing, invalid or expired",
            "403", "Authenticated user has insufficient permissions",
            "404", "Entity does not exist or is hidden from the current user",
            "409", "Business conflict with the current state");

    @Bean
    OpenAPI cloudOpsOpenApi(BuildProperties build) {
        Components components = new Components()
                .addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token: Authorization: Bearer <access token>"));
        ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
        ERRORS.forEach((status, description) -> components.addResponses(
                "Error" + status,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        "application/json",
                                        new MediaType()
                                                .schema(new Schema<>().$ref("#/components/schemas/ApiError"))))));
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CloudOps API")
                                .version(build.getVersion())
                                .description(
                                        "Infrastructure resource management, monitoring and asynchronous tasks. "
                                                + "Timestamps use UTC ISO-8601. Error responses share ApiError; errors contains field violations."))
                .components(components);
    }

    @Bean
    OpenApiCustomizer securityAndErrors() {
        return api -> api.getPaths()
                .forEach((path, item) -> item.readOperations().forEach(operation -> {
                    boolean publicEndpoint = SecurityConfiguration.PUBLIC_ENDPOINTS.contains(path);
                    operation.setSecurity(
                            publicEndpoint ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
                    List<String> statuses = publicEndpoint ? publicErrorStatuses(path) : List.copyOf(ERRORS.keySet());
                    statuses.forEach(status -> operation
                            .getResponses()
                            .putIfAbsent(status, new ApiResponse().$ref("#/components/responses/Error" + status)));
                }));
    }

    private List<String> publicErrorStatuses(String path) {
        return switch (path) {
            case "/api/auth/register" -> List.of("400", "409");
            case "/api/auth/login" -> List.of("400", "401");
            case "/api/auth/refresh" -> List.of("401");
            case "/api/auth/logout" -> List.of();
            default -> throw new IllegalArgumentException("Unexpected public endpoint: " + path);
        };
    }

    @Bean
    OpenApiCustomizer flattenPolymorphicSubtypes() {
        return api -> {
            List.of(
                            "ServerResourceConfig",
                            "NetworkDeviceResourceConfig",
                            "DatabaseResourceConfig",
                            "ServiceResourceConfig",
                            "OtherResourceConfig",
                            "UsernamePasswordCredentialRequest",
                            "SshPrivateKeyCredentialRequest")
                    .forEach(name -> {
                        Schema<?> composed = api.getComponents().getSchemas().get(name);
                        if (composed.getAllOf() == null || composed.getAllOf().size() != 2) {
                            return;
                        }
                        Schema<?> concrete = composed.getAllOf().get(1);
                        concrete.setRequired(composed.getRequired());
                        api.getComponents().addSchemas(name, concrete);
                    });
            nullableReference(api, "TaskResponse", "result", "RunCommandResult");
            nullableReference(api, "MonitorResponse", "lastResult", "ProbeExecutionResult");
        };
    }

    @Bean
    OpenApiCustomizer searchContractDocumentation() {
        Map<String, JpaSearchDefinition<?>> searches = Map.of(
                "/api/users/search", UserSearchDefinition.DEFINITION,
                "/api/organizations/search", OrganizationSearchDefinition.DEFINITION,
                "/api/organizations/{organizationId}/members/search", OrganizationMembershipSearchDefinition.DEFINITION,
                "/api/resources/search", ResourceSearchDefinition.DEFINITION,
                "/api/credentials/search", CredentialSearchDefinition.DEFINITION,
                "/api/monitors/{id}/results/search", MonitoringResultSearchDefinition.DEFINITION,
                "/api/resources/{resourceId}/health/events/search", ResourceHealthEventSearchDefinition.DEFINITION,
                "/api/tasks/search", TaskSearchDefinition.DEFINITION);
        return api -> searches.forEach(
                (path, definition) -> api.getPaths().get(path).getPost().setDescription(searchDescription(definition)));
    }

    private String searchDescription(JpaSearchDefinition<?> definition) {
        String filters = definition.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " ["
                        + entry.getValue().supportedOperations().stream()
                                .map(Enum::name)
                                .sorted()
                                .collect(Collectors.joining(", "))
                        + "]")
                .collect(Collectors.joining("; "));
        String sortable = definition.fields().entrySet().stream()
                .filter(entry -> entry.getValue().isSortable())
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.joining(", "));
        return "Allowed filter fields and operators: " + filters + ". Sortable fields: " + sortable + ". Default sort: "
                + definition.defaultSortField() + " ASC.";
    }

    private void nullableReference(OpenAPI api, String schemaName, String propertyName, String targetSchema) {
        Schema<?> property = new ComposedSchema()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/" + targetSchema))
                .nullable(true);
        api.getComponents().getSchemas().get(schemaName).getProperties().put(propertyName, property);
    }

    @Bean
    OpenApiCustomizer refreshSessionDocumentation(RefreshTokenProperties properties) {
        return api -> {
            var cookie = properties.cookie();
            String attributes = "HttpOnly; Secure=" + cookie.secure() + "; SameSite=" + cookie.sameSite() + "; Path="
                    + cookie.path() + ". Refresh token is never returned in JSON.";
            for (String action : List.of("login", "refresh", "logout")) {
                var operation = api.getPaths().get("/api/auth/" + action).getPost();
                boolean logout = action.equals("logout");
                operation
                        .getResponses()
                        .get(logout ? "204" : "200")
                        .addHeaderObject(
                                "Set-Cookie",
                                new Header()
                                        .schema(new StringSchema())
                                        .description(cookie.name() + ": "
                                                + (logout
                                                        ? "clears the refresh cookie. "
                                                        : "sets a new refresh token. ")
                                                + attributes));
                if (!action.equals("login")) {
                    operation.addParametersItem(
                            new CookieParameter()
                                    .name(cookie.name())
                                    .required(!logout)
                                    .schema(new StringSchema())
                                    .description(
                                            logout
                                                    ? "Optional refresh session to revoke. Missing/invalid token still returns 204."
                                                    : "Refresh token from login. Rotated on each successful refresh; the previous token is revoked and cannot be reused."));
                }
            }
        };
    }
}
