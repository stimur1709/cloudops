package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.monitoring")
public record MonitoringProperties(
        boolean schedulerEnabled,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @Min(1) int minimumIntervalSeconds,
        @NotNull Duration retentionPollInterval,
        @Min(1) int retentionBatchSize
) {

    public MonitoringProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(retentionPollInterval, "retentionPollInterval");
    }

    private static void requirePositive(Duration value, String property) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}
