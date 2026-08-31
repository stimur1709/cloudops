package com.github.stimur1709.cloudops.resource.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record NetworkDeviceResourceConfig(
        @NotBlank(message = "Host is required") String host,

        @Min(value = 1, message = "Management port must be between 1 and 65535") @Max(value = 65535, message = "Management port must be between 1 and 65535") Integer managementPort)
        implements ResourceConfig {}
