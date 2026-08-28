package com.github.stimur1709.cloudops.probe.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.probe.port")
public record PortCheckProperties(
        @NotNull(message = "Port check timeout is required")
        @DurationMin(nanos = 1, message = "Port check timeout must be positive")
        @DurationMax(millis = Integer.MAX_VALUE, message = "Port check timeout is too large")
        Duration timeout
) {
}
