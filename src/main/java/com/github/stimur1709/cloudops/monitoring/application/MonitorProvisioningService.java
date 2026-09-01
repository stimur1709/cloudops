package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorProvisioningService {
    private final MonitorJpaRepository monitorRepository;
    private final ResourceConfigMapper configMapper;
    private final ProbeHandlerRegistry handlerRegistry;
    private final MonitoringSettingsResolver settingsResolver;
    private final Clock clock;

    public MonitorProvisioningService(
            MonitorJpaRepository monitorRepository,
            ResourceConfigMapper configMapper,
            ProbeHandlerRegistry handlerRegistry,
            MonitoringSettingsResolver settingsResolver,
            Clock clock) {
        this.monitorRepository = monitorRepository;
        this.configMapper = configMapper;
        this.handlerRegistry = handlerRegistry;
        this.settingsResolver = settingsResolver;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(ResourceEntity resource) {
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        EnumMap<ProbeType, MonitorEntity> monitorsByType = new EnumMap<>(ProbeType.class);
        monitorRepository
                .findAllByResourceIdOrderById(resource.id())
                .forEach(monitor -> monitorsByType.put(monitor.type(), monitor));
        Instant now = clock.instant();

        for (ProbeType type : ProbeType.values()) {
            reconcileMonitor(resource, type, config, monitorsByType.get(type), now);
        }
    }

    private void reconcileMonitor(
            ResourceEntity resource, ProbeType type, ResourceConfig config, MonitorEntity monitor, Instant now) {
        boolean compatible = handlerRegistry.supports(type, config);
        boolean enabled = settingsResolver.resolve(resource, type).enabled();
        if (monitor != null) {
            monitor.updateCompatibility(compatible, enabled, now);
            if (compatible) {
                monitor.synchronizeSchedule(enabled, now);
            }
        } else if (compatible) {
            monitorRepository.insertIfAbsent(resource.id(), type.name(), enabled ? now : null);
        }
    }
}
