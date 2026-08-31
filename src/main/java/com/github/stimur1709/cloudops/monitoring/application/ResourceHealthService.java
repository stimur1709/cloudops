package com.github.stimur1709.cloudops.monitoring.application;

import java.time.Clock;
import java.util.List;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.*;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceHealthService {

    private final MonitorJpaRepository monitorRepository;
    private final ResourceHealthJpaRepository resourceHealthRepository;
    private final ResourceHealthEventJpaRepository eventRepository;
    private final Clock clock;
    private final MonitoringSettingsResolver settingsResolver;

    public ResourceHealthService(
            MonitorJpaRepository monitorRepository,
            ResourceHealthJpaRepository resourceHealthRepository,
            ResourceHealthEventJpaRepository eventRepository,
            Clock clock,
            MonitoringSettingsResolver settingsResolver
    ) {
        this.monitorRepository = monitorRepository;
        this.resourceHealthRepository = resourceHealthRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
        this.settingsResolver = settingsResolver;
    }

    @Transactional
    public ResourceHealthEntity initialize(ResourceEntity resource) {
        return resourceHealthRepository.save(ResourceHealthEntity.create(resource));
    }

    @Transactional
    public ResourceHealthEntity recalculate(long resourceId) {
        ResourceHealthEntity resourceHealth = resourceHealthRepository.findByResourceIdForUpdate(resourceId)
                .orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceHealth.resource();
        var effectiveSettings = settingsResolver.resolveAll(resource);
        List<HealthStatus> statuses = monitorRepository.findAllByResourceIdOrderById(resourceId).stream()
                .filter(MonitorEntity::compatible)
                .filter(monitor -> effectiveSettings.get(monitor.type()).enabled())
                .map(MonitorEntity::healthStatus).toList();
        ResourceHealthStatus status = aggregate(statuses);
        ResourceHealthStatus previousStatus = resourceHealth.healthStatus();
        if (previousStatus == status) {
            return resourceHealth;
        }
        resourceHealth.update(status);
        eventRepository.save(ResourceHealthEventEntity.create(
                resourceId, previousStatus, status, clock.instant()
        ));
        return resourceHealth;
    }

    static ResourceHealthStatus aggregate(List<HealthStatus> statuses) {
        if (statuses.isEmpty() || statuses.contains(HealthStatus.UNKNOWN)) {
            return ResourceHealthStatus.UNKNOWN;
        }
        boolean hasUp = statuses.contains(HealthStatus.UP);
        boolean hasDown = statuses.contains(HealthStatus.DOWN);
        if (hasUp && hasDown) {
            return ResourceHealthStatus.DEGRADED;
        }
        return hasUp ? ResourceHealthStatus.UP : ResourceHealthStatus.DOWN;
    }
}
