package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;
import java.util.Map;

import com.github.stimur1709.cloudops.monitoring.settings.DefaultProbeSettings;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @Min(1) int retentionBatchSize,
        @NotNull @Size(min = 5, max = 5) Map<ProbeType, @Valid DefaultProbeSettings> defaults
) {
    public MonitoringProperties {
        if (defaults != null) {
            for (ProbeType type : ProbeType.values()) {
                DefaultProbeSettings settings = defaults.get(type);
                if (settings == null) {
                    throw new IllegalArgumentException("Missing monitoring defaults for " + type);
                }
                if (settings.intervalSeconds() < minimumIntervalSeconds) {
                    throw new IllegalArgumentException("Default interval for " + type + " is below minimum");
                }
                boolean timeoutExpected = type != ProbeType.DNS_CHECK;
                if (timeoutExpected == (settings.timeoutMs() == null)) {
                    throw new IllegalArgumentException("Invalid timeout configuration for " + type);
                }
            }
            defaults = Map.copyOf(defaults);
        }
    }
}
