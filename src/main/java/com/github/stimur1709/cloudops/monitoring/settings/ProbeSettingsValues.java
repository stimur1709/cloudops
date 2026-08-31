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
        implements ProbeSettings {}
