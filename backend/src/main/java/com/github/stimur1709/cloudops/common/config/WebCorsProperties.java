package com.github.stimur1709.cloudops.common.config;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

@ConfigurationProperties("cloudops.web.cors")
public record WebCorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        Duration maxAge) {

    public WebCorsProperties {
        allowedOrigins = immutableList(allowedOrigins, "Allowed origins");
        allowedMethods = immutableNonEmptyList(allowedMethods, "Allowed methods");
        allowedHeaders = immutableNonEmptyList(allowedHeaders, "Allowed headers");
        exposedHeaders = immutableList(exposedHeaders, "Exposed headers");
        if (maxAge == null || maxAge.isNegative()) {
            throw new IllegalArgumentException("CORS max age must not be negative");
        }
        allowedOrigins.forEach(WebCorsProperties::validateOrigin);
        rejectWildcard(allowedMethods, "Allowed methods");
        allowedMethods.forEach(WebCorsProperties::validateMethod);
        rejectWildcard(allowedHeaders, "Allowed headers");
        rejectWildcard(exposedHeaders, "Exposed headers");
    }

    private static List<String> immutableNonEmptyList(List<String> values, String name) {
        List<String> result = immutableList(values, name);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return result;
    }

    private static List<String> immutableList(List<String> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must not contain blank values");
        }
        return List.copyOf(values);
    }

    private static void validateOrigin(String value) {
        if ("*".equals(value)) {
            throw new IllegalArgumentException("Wildcard CORS origins are not allowed");
        }
        URI origin;
        try {
            origin = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid CORS origin: " + value, exception);
        }
        boolean http = "http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme());
        if (!http
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getQuery() != null
                || origin.getFragment() != null) {
            throw new IllegalArgumentException("CORS origin must be an HTTP(S) origin without a path: " + value);
        }
    }

    private static void rejectWildcard(List<String> values, String name) {
        if (values.contains("*")) {
            throw new IllegalArgumentException(name + " must not contain a wildcard");
        }
    }

    private static void validateMethod(String value) {
        if (Arrays.stream(HttpMethod.values()).noneMatch(method -> method.matches(value))) {
            throw new IllegalArgumentException("Invalid CORS HTTP method: " + value);
        }
    }
}
