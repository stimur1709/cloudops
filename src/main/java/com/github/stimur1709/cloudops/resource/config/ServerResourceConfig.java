package com.github.stimur1709.cloudops.resource.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ServerResourceConfig(
        @NotBlank(message = "Host is required") String host,

        @Min(value = 1, message = "Port must be between 1 and 65535") @Max(value = 65535, message = "Port must be between 1 and 65535") Integer port,

        @Min(value = 1, message = "SSH port must be between 1 and 65535") @Max(value = 65535, message = "SSH port must be between 1 and 65535") Integer sshPort)
        implements ResourceConfig {

    public ServerResourceConfig {
        if (sshPort == null) {
            sshPort = 22;
        }
    }

    public ServerResourceConfig(String host, Integer port) {
        this(host, port, null);
    }
}
