package com.github.stimur1709.cloudops.monitoring.settings;

import com.github.stimur1709.cloudops.monitoring.StorageMode;

public record ProbeSettingsValues(
        boolean enabled,
        int intervalSeconds,
        int failureThreshold,
        int recoveryThreshold,
        StorageMode storageMode,
        Integer retentionDays,
        Integer timeoutMs)
        implements ProbeSettings {

    public static ProbeSettingsValues from(ProbeSettings settings) {
        return new ProbeSettingsValues(
                settings.enabled(),
                settings.intervalSeconds(),
                settings.failureThreshold(),
                settings.recoveryThreshold(),
                settings.storageMode(),
                settings.retentionDays(),
                settings.timeoutMs());
    }
}
