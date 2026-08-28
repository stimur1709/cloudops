package com.github.stimur1709.cloudops.monitoring.execution;

import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultJpaRepository;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class MonitorExecutionPersistenceService {

    private final MonitorJpaRepository monitorRepository;
    private final MonitoringResultJpaRepository resultRepository;
    private final ResourceJpaRepository resourceRepository;
    private final ResourceConfigMapper configMapper;

    public MonitorExecutionPersistenceService(
            MonitorJpaRepository monitorRepository,
            MonitoringResultJpaRepository resultRepository,
            ResourceJpaRepository resourceRepository,
            ResourceConfigMapper configMapper
    ) {
        this.monitorRepository = monitorRepository;
        this.resultRepository = resultRepository;
        this.resourceRepository = resourceRepository;
        this.configMapper = configMapper;
    }

    @Transactional(readOnly = true)
    public MonitorExecutionContext loadIfExecutable(long monitorId) {
        MonitorEntity monitor = monitorRepository.findById(monitorId).orElse(null);
        if (monitor == null || !monitor.enabled()) {
            return null;
        }
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElse(null);
        if (resource == null || resource.status() != ResourceStatus.ACTIVE) {
            return null;
        }
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        return new MonitorExecutionContext(
                monitor.id(), resource.id(), monitor.type(), config
        );
    }

    @Transactional
    public void saveResult(long monitorId, Instant checkedAt, JsonNode result, boolean success) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(monitorId)
                .orElseThrow(NotFoundException::new);
        if (!monitor.enabled()) {
            return;
        }
        monitor.record(checkedAt, result, success);
        if (monitor.storageMode() == StorageMode.HISTORY) {
            resultRepository.save(MonitoringResultEntity.create(monitorId, checkedAt, result));
        }
    }
}
