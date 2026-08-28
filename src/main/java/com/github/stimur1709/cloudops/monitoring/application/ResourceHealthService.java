package com.github.stimur1709.cloudops.monitoring.application;

import java.util.List;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceHealthService {

    private final MonitorJpaRepository monitorRepository;
    private final ResourceHealthJpaRepository resourceHealthRepository;

    public ResourceHealthService(
            MonitorJpaRepository monitorRepository,
            ResourceHealthJpaRepository resourceHealthRepository
    ) {
        this.monitorRepository = monitorRepository;
        this.resourceHealthRepository = resourceHealthRepository;
    }

    @Transactional
    public ResourceHealthEntity initialize(ResourceEntity resource) {
        return resourceHealthRepository.save(ResourceHealthEntity.create(resource));
    }

    @Transactional
    public ResourceHealthEntity recalculate(long resourceId) {
        ResourceHealthEntity resourceHealth = resourceHealthRepository.findByResourceIdForUpdate(resourceId)
                .orElseThrow(NotFoundException::new);
        ResourceHealthStatus status = aggregate(
                monitorRepository.findHealthStatusesByResourceIdAndEnabledTrue(resourceId)
        );
        resourceHealth.update(status);
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
