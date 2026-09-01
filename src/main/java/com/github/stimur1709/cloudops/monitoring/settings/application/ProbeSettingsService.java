package com.github.stimur1709.cloudops.monitoring.settings.application;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.EffectiveProbeSettings;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.monitoring.settings.ProbeSettings;
import com.github.stimur1709.cloudops.monitoring.settings.ProbeSettingsValues;
import com.github.stimur1709.cloudops.monitoring.settings.SettingsSource;
import com.github.stimur1709.cloudops.monitoring.settings.api.ProbeSettingsRequest;
import com.github.stimur1709.cloudops.monitoring.settings.api.ProbeSettingsResponse;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProbeSettingsService {

    private final OrganizationJpaRepository organizationRepository;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationProbeSettingsJpaRepository organizationSettingsRepository;
    private final ResourceProbeSettingsJpaRepository resourceSettingsRepository;
    private final OrganizationAuthorization authorization;
    private final MonitoringSettingsResolver resolver;
    private final MonitoringProperties properties;
    private final ProbeHandlerRegistry handlerRegistry;
    private final ResourceConfigMapper configMapper;
    private final MonitoringSettingsSynchronizer synchronizer;

    public ProbeSettingsService(
            OrganizationJpaRepository organizationRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationProbeSettingsJpaRepository organizationSettingsRepository,
            ResourceProbeSettingsJpaRepository resourceSettingsRepository,
            OrganizationAuthorization authorization,
            MonitoringSettingsResolver resolver,
            MonitoringProperties properties,
            ProbeHandlerRegistry handlerRegistry,
            ResourceConfigMapper configMapper,
            MonitoringSettingsSynchronizer synchronizer) {
        this.organizationRepository = organizationRepository;
        this.resourceRepository = resourceRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.resourceSettingsRepository = resourceSettingsRepository;
        this.authorization = authorization;
        this.resolver = resolver;
        this.properties = properties;
        this.handlerRegistry = handlerRegistry;
        this.configMapper = configMapper;
        this.synchronizer = synchronizer;
    }

    @Transactional(readOnly = true)
    public List<ProbeSettingsResponse> listOrganization(long organizationId, long userId) {
        requireOrganization(organizationId);
        authorization.requireMember(organizationId, userId);
        Map<ProbeType, OrganizationProbeSettingsEntity> ownSettings =
                organizationSettingsRepository.findAllByOrganizationId(organizationId).stream()
                        .collect(Collectors.toMap(OrganizationProbeSettingsEntity::probeType, Function.identity()));
        return Arrays.stream(ProbeType.values())
                .map(type -> {
                    var own = ownSettings.get(type);
                    EffectiveProbeSettings effective = own != null
                            ? effective(type, own, SettingsSource.ORGANIZATION)
                            : effective(type, properties.defaults().get(type), SettingsSource.APPLICATION);
                    return new ProbeSettingsResponse(type, true, effective.source(), effective, false);
                })
                .toList();
    }

    @Transactional
    public ProbeSettingsResponse putOrganization(
            long organizationId, ProbeType type, ProbeSettingsRequest request, long userId) {
        requireOrganization(organizationId);
        authorization.requireManager(organizationId, userId);
        ProbeSettings values = values(request);
        var entity = organizationSettingsRepository
                .findByOrganizationIdAndProbeType(organizationId, type)
                .orElseGet(() -> OrganizationProbeSettingsEntity.create(organizationId, type, values));
        entity.update(values);
        organizationSettingsRepository.saveAndFlush(entity);
        afterCommit(() -> synchronizer.putOrganization(organizationId, type, values));
        EffectiveProbeSettings effective = effective(type, entity, SettingsSource.ORGANIZATION);
        return new ProbeSettingsResponse(type, true, effective.source(), effective, false);
    }

    @Transactional
    public void deleteOrganization(long organizationId, ProbeType type, long userId) {
        requireOrganization(organizationId);
        authorization.requireManager(organizationId, userId);
        organizationSettingsRepository.deleteByOrganizationIdAndProbeType(organizationId, type);
        organizationSettingsRepository.flush();
        afterCommit(() -> synchronizer.removeOrganization(organizationId, type));
    }

    @Transactional(readOnly = true)
    public List<ProbeSettingsResponse> listResource(long resourceId, long userId) {
        ResourceEntity resource = requireResource(resourceId);
        authorization.requireMember(resource.organizationId(), userId);
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        var effectiveSettings = resolver.resolveAll(resource);
        return Arrays.stream(ProbeType.values())
                .map(type -> {
                    EffectiveProbeSettings effective = effectiveSettings.get(type);
                    boolean own = effective.source() == SettingsSource.RESOURCE;
                    return new ProbeSettingsResponse(
                            type, handlerRegistry.supports(type, config), effective.source(), effective, own);
                })
                .toList();
    }

    @Transactional
    public ProbeSettingsResponse putResource(
            long resourceId, ProbeType type, ProbeSettingsRequest request, long userId) {
        ResourceEntity resource = requireResource(resourceId);
        authorization.requireManager(resource.organizationId(), userId);
        ProbeSettings values = values(request);
        var entity = resourceSettingsRepository
                .findByResourceIdAndProbeType(resourceId, type)
                .orElseGet(() -> ResourceProbeSettingsEntity.create(resourceId, type, values));
        entity.update(values);
        resourceSettingsRepository.saveAndFlush(entity);
        afterCommit(() -> synchronizer.putResource(resourceId, type, values));
        EffectiveProbeSettings effective = effective(type, entity, SettingsSource.RESOURCE);
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        return new ProbeSettingsResponse(
                type, handlerRegistry.supports(type, config), effective.source(), effective, true);
    }

    @Transactional
    public void deleteResource(long resourceId, ProbeType type, long userId) {
        ResourceEntity resource = requireResource(resourceId);
        authorization.requireManager(resource.organizationId(), userId);
        resourceSettingsRepository.deleteByResourceIdAndProbeType(resourceId, type);
        resourceSettingsRepository.flush();
        afterCommit(() -> synchronizer.removeResource(resourceId, type));
    }

    private ResourceEntity requireResource(long id) {
        return resourceRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    private void requireOrganization(long id) {
        if (!organizationRepository.existsById(id)) {
            throw new NotFoundException();
        }
    }

    private ProbeSettings values(ProbeSettingsRequest request) {
        return new ProbeSettingsValues(
                request.enabled(),
                request.intervalSeconds(),
                request.failureThreshold(),
                request.recoveryThreshold(),
                request.storageMode(),
                request.retentionDays(),
                request.timeoutMs());
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
                source);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
