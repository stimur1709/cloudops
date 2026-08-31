package com.github.stimur1709.cloudops.task.outbox;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("cloudops.task.outbox")
@Validated
public record OutboxRelayProperties(
        boolean enabled,
        @NotNull Duration pollInterval,
        @Positive int batchSize) {}
