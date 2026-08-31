package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.application.ResourceHealthService;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.EffectiveProbeSettings;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class MonitorExecutionPersistenceService {

    private final MonitorJpaRepository monitorRepository;
    private final MonitoringResultJpaRepository resultRepository;
    private final ResourceJpaRepository resourceRepository;
    private final ResourceConfigMapper configMapper;
    private final ResourceHealthService resourceHealthService;
    private final MonitoringSettingsResolver settingsResolver;
    private final Clock clock;

    public MonitorExecutionPersistenceService(
            MonitorJpaRepository monitorRepository,
            MonitoringResultJpaRepository resultRepository,
            ResourceJpaRepository resourceRepository,
            ResourceConfigMapper configMapper,
            ResourceHealthService resourceHealthService,
            MonitoringSettingsResolver settingsResolver,
            Clock clock) {
        this.monitorRepository = monitorRepository;
        this.resultRepository = resultRepository;
        this.resourceRepository = resourceRepository;
        this.configMapper = configMapper;
        this.resourceHealthService = resourceHealthService;
        this.settingsResolver = settingsResolver;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonitorExecutionContext loadIfExecutable(long monitorId) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(monitorId).orElse(null);
        if (monitor == null || !monitor.compatible()) {
            return null;
        }
        ResourceEntity resource =
                resourceRepository.findById(monitor.resourceId()).orElse(null);
        if (resource == null) {
            return null;
        }
        EffectiveProbeSettings settings = settingsResolver.resolve(resource, monitor.type());
        monitor.scheduleNext(clock.instant(), settings.intervalSeconds());
        if (!settings.enabled() || resource.status() != ResourceStatus.ACTIVE) {
            return null;
        }
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        return new MonitorExecutionContext(monitor.id(), resource.id(), monitor.type(), config, settings);
    }

    @Transactional
    public void saveResult(long monitorId, Instant checkedAt, JsonNode result, boolean success) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(monitorId).orElseThrow(NotFoundException::new);
        EffectiveProbeSettings settings = settingsResolver.resolve(monitor.resourceId(), monitor.type());
        if (!settings.enabled()) {
            return;
        }
        monitor.record(checkedAt, result, success, settings.failureThreshold(), settings.recoveryThreshold());
        if (settings.storageMode() == StorageMode.HISTORY) {
            resultRepository.save(MonitoringResultEntity.create(monitorId, checkedAt, result));
        }
        monitorRepository.flush();
        resourceHealthService.recalculate(monitor.resourceId());
    }
}
