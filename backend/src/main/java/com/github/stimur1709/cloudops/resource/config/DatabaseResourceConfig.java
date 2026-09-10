package com.github.stimur1709.cloudops.resource.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DatabaseResourceConfig(
        @NotBlank(message = "Host is required") String host,

        @NotNull(message = "Port is required") @Min(value = 1, message = "Port must be between 1 and 65535") @Max(value = 65535, message = "Port must be between 1 and 65535") Integer port,

        @NotBlank(message = "Database is required") String database)
        implements ResourceConfig {}
