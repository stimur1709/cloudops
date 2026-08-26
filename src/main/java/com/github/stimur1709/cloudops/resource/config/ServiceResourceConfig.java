package com.github.stimur1709.cloudops.resource.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ServiceResourceConfig(
        @NotBlank(message = "URL is required")
        @HttpUrl
        String url,

        @Min(value = 100, message = "Expected status must be between 100 and 599")
        @Max(value = 599, message = "Expected status must be between 100 and 599")
        Integer expectedStatus,

        @Positive(message = "Timeout must be positive")
        @Max(value = 60000, message = "Timeout must be at most 60000 milliseconds")
        Integer timeoutMs
) implements ResourceConfig {

    public ServiceResourceConfig {
        expectedStatus = expectedStatus == null ? 200 : expectedStatus;
        timeoutMs = timeoutMs == null ? 5000 : timeoutMs;
    }
}
