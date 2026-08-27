package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.api.validation.MonitorSettings;
import com.github.stimur1709.cloudops.monitoring.api.validation.ValidMonitorSettings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ValidMonitorSettings
public record UpdateMonitorRequest(
        @NotNull(message = "Interval is required") @Positive(message = "Interval must be positive")
        Integer intervalSeconds,
        @NotNull(message = "Enabled is required") Boolean enabled,
        @NotNull(message = "Storage mode is required") StorageMode storageMode,
        Integer retentionDays
) implements MonitorSettings {
}
