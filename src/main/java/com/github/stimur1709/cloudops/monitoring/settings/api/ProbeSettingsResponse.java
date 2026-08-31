package com.github.stimur1709.cloudops.monitoring.settings.api;

import com.github.stimur1709.cloudops.monitoring.settings.EffectiveProbeSettings;
import com.github.stimur1709.cloudops.monitoring.settings.SettingsSource;
import com.github.stimur1709.cloudops.probe.ProbeType;

public record ProbeSettingsResponse(
        ProbeType probeType,
        boolean supported,
        SettingsSource source,
        EffectiveProbeSettings effective,
        boolean resourceOverride
) {
}
