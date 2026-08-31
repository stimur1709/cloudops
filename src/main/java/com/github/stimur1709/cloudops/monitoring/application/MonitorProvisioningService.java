package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
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
    private final Clock clock;

    public MonitorProvisioningService(
            MonitorJpaRepository monitorRepository,
            ResourceConfigMapper configMapper,
            ProbeHandlerRegistry handlerRegistry,
            Clock clock) {
        this.monitorRepository = monitorRepository;
        this.configMapper = configMapper;
        this.handlerRegistry = handlerRegistry;
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
            reconcileMonitor(resource.id(), type, config, monitorsByType.get(type), now);
        }
    }

    private void reconcileMonitor(
            long resourceId, ProbeType type, ResourceConfig config, MonitorEntity monitor, Instant now) {
        boolean compatible = handlerRegistry.supports(type, config);
        if (monitor != null) {
            monitor.updateCompatibility(compatible, now);
        } else if (compatible) {
            monitorRepository.insertIfAbsent(resourceId, type.name(), now);
        }
    }
}
