package com.github.stimur1709.cloudops.monitoring.settings;

import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonitoringSettingsIndex implements ApplicationRunner {

    private final OrganizationProbeSettingsJpaRepository organizationRepository;
    private final ResourceProbeSettingsJpaRepository resourceRepository;
    private final Map<OrganizationKey, ProbeSettingsValues> organizationOverrides = new ConcurrentHashMap<>();
    private final Map<ResourceKey, ProbeSettingsValues> resourceOverrides = new ConcurrentHashMap<>();

    public MonitoringSettingsIndex(
            OrganizationProbeSettingsJpaRepository organizationRepository,
            ResourceProbeSettingsJpaRepository resourceRepository) {
        this.organizationRepository = organizationRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    public void reload() {
        organizationOverrides.clear();
        organizationRepository
                .findAll()
                .forEach(settings -> putOrganization(
                        settings.organizationId(), settings.probeType(), ProbeSettingsValues.from(settings)));
        resourceOverrides.clear();
        resourceRepository
                .findAll()
                .forEach(settings ->
                        putResource(settings.resourceId(), settings.probeType(), ProbeSettingsValues.from(settings)));
    }

    public ProbeSettings organization(long organizationId, ProbeType type) {
        return organizationOverrides.get(new OrganizationKey(organizationId, type));
    }

    public ProbeSettings resource(long resourceId, ProbeType type) {
        return resourceOverrides.get(new ResourceKey(resourceId, type));
    }

    public void putOrganization(long organizationId, ProbeType type, ProbeSettings settings) {
        organizationOverrides.put(new OrganizationKey(organizationId, type), ProbeSettingsValues.from(settings));
    }

    public void removeOrganization(long organizationId, ProbeType type) {
        organizationOverrides.remove(new OrganizationKey(organizationId, type));
    }

    public void putResource(long resourceId, ProbeType type, ProbeSettings settings) {
        resourceOverrides.put(new ResourceKey(resourceId, type), ProbeSettingsValues.from(settings));
    }

    public void removeResource(long resourceId, ProbeType type) {
        resourceOverrides.remove(new ResourceKey(resourceId, type));
    }

    int organizationOverrideCount() {
        return organizationOverrides.size();
    }

    int resourceOverrideCount() {
        return resourceOverrides.size();
    }

    private record OrganizationKey(long organizationId, ProbeType type) {}

    private record ResourceKey(long resourceId, ProbeType type) {}
}
