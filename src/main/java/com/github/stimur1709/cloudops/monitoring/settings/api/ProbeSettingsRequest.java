package com.github.stimur1709.cloudops.monitoring.settings.api;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.settings.api.validation.ValidProbeSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ValidProbeSettings
public record ProbeSettingsRequest(
        @NotNull(message = "Enabled is required") Boolean enabled,

        @NotNull(message = "Interval is required") @Positive(message = "Interval must be positive") Integer intervalSeconds,

        @NotNull(message = "Failure threshold is required") @Min(value = 1, message = "Failure threshold must be between 1 and 10") @Max(value = 10, message = "Failure threshold must be between 1 and 10") Integer failureThreshold,

        @NotNull(message = "Recovery threshold is required") @Min(value = 1, message = "Recovery threshold must be between 1 and 10") @Max(value = 10, message = "Recovery threshold must be between 1 and 10") Integer recoveryThreshold,

        @NotNull(message = "Storage mode is required") StorageMode storageMode,
        Integer retentionDays,
        Integer timeoutMs) {}
