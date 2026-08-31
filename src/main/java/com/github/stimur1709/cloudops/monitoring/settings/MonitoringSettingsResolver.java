package com.github.stimur1709.cloudops.monitoring.settings;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringSettingsResolver {

    private final ResourceJpaRepository resourceRepository;
    private final ResourceProbeSettingsJpaRepository resourceSettingsRepository;
    private final OrganizationProbeSettingsJpaRepository organizationSettingsRepository;
    private final MonitoringProperties properties;

    public MonitoringSettingsResolver(
            ResourceJpaRepository resourceRepository,
            ResourceProbeSettingsJpaRepository resourceSettingsRepository,
            OrganizationProbeSettingsJpaRepository organizationSettingsRepository,
            MonitoringProperties properties
    ) {
        this.resourceRepository = resourceRepository;
        this.resourceSettingsRepository = resourceSettingsRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public EffectiveProbeSettings resolve(long resourceId, ProbeType probeType) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        return resolve(resource, probeType);
    }

    public EffectiveProbeSettings resolve(ResourceEntity resource, ProbeType probeType) {
        return resourceSettingsRepository.findByResourceIdAndProbeType(resource.id(), probeType)
                .map(settings -> effective(probeType, settings, SettingsSource.RESOURCE))
                .orElseGet(() -> organizationSettingsRepository
                        .findByOrganizationIdAndProbeType(resource.organizationId(), probeType)
                        .map(settings -> effective(probeType, settings, SettingsSource.ORGANIZATION))
                        .orElseGet(() -> effective(
                                probeType, properties.defaults().get(probeType), SettingsSource.APPLICATION
                        )));
    }

    public Map<ProbeType, EffectiveProbeSettings> resolveAll(ResourceEntity resource) {
        var resourceSettings = resourceSettingsRepository.findAllByResourceId(resource.id()).stream()
                .collect(Collectors.toMap(ResourceProbeSettingsEntity::probeType, Function.identity()));
        var organizationSettings = organizationSettingsRepository
                .findAllByOrganizationId(resource.organizationId()).stream()
                .collect(Collectors.toMap(OrganizationProbeSettingsEntity::probeType, Function.identity()));
        var effectiveSettings = new EnumMap<ProbeType, EffectiveProbeSettings>(ProbeType.class);
        for (ProbeType type : ProbeType.values()) {
            ProbeSettings settings = resourceSettings.get(type);
            SettingsSource source = SettingsSource.RESOURCE;
            if (settings == null) {
                settings = organizationSettings.get(type);
                source = SettingsSource.ORGANIZATION;
            }
            if (settings == null) {
                settings = properties.defaults().get(type);
                source = SettingsSource.APPLICATION;
            }
            effectiveSettings.put(type, effective(type, settings, source));
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
                source
        );
    }
}
