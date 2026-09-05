package com.github.stimur1709.cloudops.monitoring.settings.application;

import com.github.stimur1709.cloudops.monitoring.application.ResourceHealthService;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsIndex;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.monitoring.settings.ProbeSettings;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringSettingsSynchronizer {

    private final MonitoringSettingsIndex index;
    private final MonitoringSettingsResolver resolver;
    private final ResourceJpaRepository resourceRepository;
    private final MonitorJpaRepository monitorRepository;
    private final ResourceHealthService resourceHealthService;
    private final Clock clock;

    public MonitoringSettingsSynchronizer(
            MonitoringSettingsIndex index,
            MonitoringSettingsResolver resolver,
            ResourceJpaRepository resourceRepository,
            MonitorJpaRepository monitorRepository,
            ResourceHealthService resourceHealthService,
            Clock clock) {
        this.index = index;
        this.resolver = resolver;
        this.resourceRepository = resourceRepository;
        this.monitorRepository = monitorRepository;
        this.resourceHealthService = resourceHealthService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void putOrganization(long organizationId, ProbeType type, ProbeSettings settings) {
        index.putOrganization(organizationId, type, settings);
        reconcileOrganization(organizationId, type);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeOrganization(long organizationId, ProbeType type) {
        index.removeOrganization(organizationId, type);
        reconcileOrganization(organizationId, type);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void putResource(long resourceId, ProbeType type, ProbeSettings settings) {
        index.putResource(resourceId, type, settings);
        reconcileResource(resourceId, type);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeResource(long resourceId, ProbeType type) {
        index.removeResource(resourceId, type);
        reconcileResource(resourceId, type);
    }

    private void reconcileOrganization(long organizationId, ProbeType type) {
        resourceRepository.findAllByOrganizationId(organizationId).forEach(resource -> reconcile(resource, type));
    }

    private void reconcileResource(long resourceId, ProbeType type) {
        resourceRepository.findById(resourceId).ifPresent(resource -> reconcile(resource, type));
    }

    private void reconcile(ResourceEntity resource, ProbeType type) {
        boolean enabled = resolver.resolve(resource, type).enabled();
        monitorRepository
                .findByResourceIdAndType(resource.id(), type)
                .ifPresent(monitor -> monitor.synchronizeSchedule(enabled, clock.instant()));
        resourceHealthService.recalculate(resource.id());
    }
}
