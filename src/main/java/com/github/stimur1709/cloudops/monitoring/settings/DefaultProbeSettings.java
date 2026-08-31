package com.github.stimur1709.cloudops.monitoring.settings;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public record DefaultProbeSettings(
        boolean enabled,
        @Min(1) int intervalSeconds,
        @Min(1) @Max(10) int failureThreshold,
        @Min(1) @Max(10) int recoveryThreshold,
        @NotNull StorageMode storageMode,
        @Min(1) @Max(365) Integer retentionDays,
        @Min(1) @Max(60000) Integer timeoutMs
) implements ProbeSettings {
    @AssertTrue(message = "Retention days must be set only for HISTORY storage mode")
    public boolean isRetentionValid() {
        return storageMode == StorageMode.HISTORY ? retentionDays != null : retentionDays == null;
    }
}
