package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateMonitorRequest(
        @NotNull(message = "Interval is required") @Positive(message = "Interval must be positive")
        Integer intervalSeconds,
        @NotNull(message = "Enabled is required") Boolean enabled,
        @NotNull(message = "Storage mode is required") StorageMode storageMode,
        Integer retentionDays
) {
}
