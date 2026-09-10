package com.github.stimur1709.cloudops.monitoring.settings;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.probe.ProbeType;

public record EffectiveProbeSettings(
        ProbeType probeType,
        boolean enabled,
        int intervalSeconds,
        int failureThreshold,
        int recoveryThreshold,
        StorageMode storageMode,
        Integer retentionDays,
        Integer timeoutMs,
        SettingsSource source)
        implements ProbeSettings {}
