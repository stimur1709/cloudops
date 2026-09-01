package com.github.stimur1709.cloudops.monitoring.settings;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MonitoringSettingsResolver {

    private final MonitoringSettingsIndex index;
    private final MonitoringProperties properties;

    public MonitoringSettingsResolver(MonitoringSettingsIndex index, MonitoringProperties properties) {
        this.index = index;
        this.properties = properties;
    }

    public EffectiveProbeSettings resolve(ResourceEntity resource, ProbeType probeType) {
        return resolve(resource.id(), resource.organizationId(), probeType);
    }

    public EffectiveProbeSettings resolve(long resourceId, long organizationId, ProbeType probeType) {
        ProbeSettings settings = index.resource(resourceId, probeType);
        if (settings != null) {
            return effective(probeType, settings, SettingsSource.RESOURCE);
        }
        settings = index.organization(organizationId, probeType);
        if (settings != null) {
            return effective(probeType, settings, SettingsSource.ORGANIZATION);
        }
        return effective(probeType, properties.defaults().get(probeType), SettingsSource.APPLICATION);
    }

    public Map<ProbeType, EffectiveProbeSettings> resolveAll(ResourceEntity resource) {
        var effectiveSettings = new EnumMap<ProbeType, EffectiveProbeSettings>(ProbeType.class);
        for (ProbeType type : ProbeType.values()) {
            effectiveSettings.put(type, resolve(resource, type));
        }
        return Map.copyOf(effectiveSettings);
    }

    private EffectiveProbeSettings effective(ProbeType type, ProbeSettings settings, SettingsSource source) {
        return new EffectiveProbeSettings(
                type,
                settings.enabled(),
                settings.intervalSeconds(),
                settings.failureThreshold(),
                settings.recoveryThreshold(),
                settings.storageMode(),
                settings.retentionDays(),
                settings.timeoutMs(),
                source);
    }
}
