package com.github.stimur1709.cloudops.task.outbox;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("cloudops.task.outbox")
@Validated
public record OutboxRelayProperties(
        boolean enabled,
        @NotNull Duration pollInterval,
        @Positive int batchSize
) {
}
