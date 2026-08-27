package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.monitoring")
public record MonitoringProperties(
        boolean schedulerEnabled,
        @NotNull @DurationMin(nanos = 1) Duration pollInterval,
        @Min(1) int batchSize,
        @Min(1) int minimumIntervalSeconds,
        @NotNull @DurationMin(nanos = 1) Duration retentionPollInterval,
        @Min(1) int retentionBatchSize
) {
}
