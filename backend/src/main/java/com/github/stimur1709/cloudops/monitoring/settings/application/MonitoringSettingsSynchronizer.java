package com.github.stimur1709.cloudops.monitoring.settings.application;

import com.github.stimur1709.cloudops.monitoring.application.ResourceHealthService;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsIndex;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsJpaRepository;
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
    private final OrganizationProbeSettingsJpaRepository organizationSettings;
    private final ResourceProbeSettingsJpaRepository resourceSettings;

    public MonitoringSettingsSynchronizer(
            MonitoringSettingsIndex index,
            MonitoringSettingsResolver resolver,
            ResourceJpaRepository resourceRepository,
            MonitorJpaRepository monitorRepository,
            ResourceHealthService resourceHealthService,
            Clock clock,
            OrganizationProbeSettingsJpaRepository organizationSettings,
            ResourceProbeSettingsJpaRepository resourceSettings) {
        this.index = index;
        this.resolver = resolver;
        this.resourceRepository = resourceRepository;
        this.monitorRepository = monitorRepository;
        this.resourceHealthService = resourceHealthService;
        this.clock = clock;
        this.organizationSettings = organizationSettings;
        this.resourceSettings = resourceSettings;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronizeOrganization(long organizationId, ProbeType type, boolean recovery) {
        organizationSettings
                .findByOrganizationIdAndProbeType(organizationId, type)
                .ifPresentOrElse(
                        settings -> index.putOrganization(organizationId, type, settings),
                        () -> index.removeOrganization(organizationId, type));
        resourceRepository
                .findAllByOrganizationId(organizationId)
                .forEach(resource -> reconcile(resource, type, recovery));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronizeResource(long resourceId, ProbeType type, boolean recovery) {
        resourceSettings
                .findByResourceIdAndProbeType(resourceId, type)
                .ifPresentOrElse(
                        settings -> index.putResource(resourceId, type, settings),
                        () -> index.removeResource(resourceId, type));
        resourceRepository.findById(resourceId).ifPresent(resource -> reconcile(resource, type, recovery));
    }

    private void reconcile(ResourceEntity resource, ProbeType type, boolean recovery) {
        boolean enabled = resolver.resolve(resource, type).enabled();
        monitorRepository.findByResourceIdAndTypeForUpdate(resource.id(), type).ifPresent(monitor -> {
            if (recovery) {
                monitor.repairSchedule(enabled, clock.instant());
            } else {
                monitor.synchronizeSchedule(enabled, clock.instant());
            }
        });
        resourceHealthService.recalculate(resource.id());
    }
}
