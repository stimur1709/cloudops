package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorHealth;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import java.time.Clock;
import java.util.List;
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
            MonitoringSettingsResolver settingsResolver) {
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
        ResourceHealthEntity resourceHealth =
                resourceHealthRepository.findByResourceIdForUpdate(resourceId).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceHealth.resource();
        var effectiveSettings = settingsResolver.resolveAll(resource);
        List<HealthStatus> statuses = monitorRepository.findHealthByResourceId(resourceId).stream()
                .filter(MonitorHealth::isCompatible)
                .filter(monitor -> effectiveSettings.get(monitor.getType()).enabled())
                .map(MonitorHealth::getHealthStatus)
                .toList();
        ResourceHealthStatus status = aggregate(statuses);
        ResourceHealthStatus previousStatus = resourceHealth.healthStatus();
        if (previousStatus == status) {
            return resourceHealth;
        }
        resourceHealth.update(status);
        eventRepository.save(ResourceHealthEventEntity.create(resourceId, previousStatus, status, clock.instant()));
        return resourceHealth;
    }

    static ResourceHealthStatus aggregate(List<HealthStatus> statuses) {
        boolean hasUp = statuses.contains(HealthStatus.UP);
        boolean hasDown = statuses.contains(HealthStatus.DOWN);
        if (!hasUp && !hasDown) {
            return ResourceHealthStatus.UNKNOWN;
        }
        if (hasUp && hasDown) {
            return ResourceHealthStatus.DEGRADED;
        }
        return hasUp ? ResourceHealthStatus.UP : ResourceHealthStatus.DOWN;
    }
}
