package com.github.stimur1709.cloudops.monitoring.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorProvisioningService {
    private final MonitorJpaRepository monitorRepository;
    private final ResourceConfigMapper configMapper;
    private final ProbeHandlerRegistry handlerRegistry;
    private final Clock clock;

    public MonitorProvisioningService(MonitorJpaRepository monitorRepository, ResourceConfigMapper configMapper,
                                      ProbeHandlerRegistry handlerRegistry, Clock clock) {
        this.monitorRepository = monitorRepository;
        this.configMapper = configMapper;
        this.handlerRegistry = handlerRegistry;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(ResourceEntity resource) {
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        var existing = monitorRepository.findAllByResourceIdOrderById(resource.id()).stream()
                .collect(Collectors.toMap(MonitorEntity::type, Function.identity(), (first, second) -> first,
                        () -> new EnumMap<>(ProbeType.class)));
        Arrays.stream(ProbeType.values()).forEach(type -> {
            boolean compatible = handlerRegistry.supports(type, config);
            MonitorEntity monitor = existing.get(type);
            if (monitor == null) {
                if (compatible) {
                    create(resource.id(), type);
                }
            } else {
                monitor.setCompatible(compatible, clock.instant());
            }
        });
    }

    private void create(long resourceId, ProbeType type) {
        try {
            monitorRepository.saveAndFlush(MonitorEntity.create(resourceId, type, clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // The unique constraint makes concurrent reconciliation idempotent.
        }
    }
}
