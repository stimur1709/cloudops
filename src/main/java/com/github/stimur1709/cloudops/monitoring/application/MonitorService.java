package com.github.stimur1709.cloudops.monitoring.application;

import java.time.Clock;
import java.util.List;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScopes;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.api.CreateMonitorRequest;
import com.github.stimur1709.cloudops.monitoring.api.UpdateMonitorRequest;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity_;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultSearchDefinition;
import com.github.stimur1709.cloudops.monitoring.execution.MonitorExecutionService;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private final MonitorJpaRepository monitorRepository;
    private final MonitoringResultJpaRepository resultRepository;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final JpaSearchService searchService;
    private final ResourceHealthService resourceHealthService;
    private final Clock clock;
    private final ResourceConfigMapper configMapper;
    private final ProbeHandlerRegistry handlerRegistry;
    private final MonitorExecutionService executionService;

    public MonitorService(
            MonitorJpaRepository monitorRepository,
            MonitoringResultJpaRepository resultRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            JpaSearchService searchService,
            ResourceHealthService resourceHealthService,
            Clock clock,
            ResourceConfigMapper configMapper,
            ProbeHandlerRegistry handlerRegistry,
            MonitorExecutionService executionService
    ) {
        this.monitorRepository = monitorRepository;
        this.resultRepository = resultRepository;
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.searchService = searchService;
        this.resourceHealthService = resourceHealthService;
        this.clock = clock;
        this.configMapper = configMapper;
        this.handlerRegistry = handlerRegistry;
        this.executionService = executionService;
    }

    @Transactional
    public MonitorEntity create(
            long resourceId,
            CreateMonitorRequest request,
            long currentUserId
    ) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        requireSupported(resource, request.type());
        if (resource.status() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_INACTIVE", "Monitoring requires an active resource");
        }
        if (monitorRepository.existsByResourceIdAndType(resourceId, request.type())) {
            throw monitorConflict();
        }
        MonitorEntity monitor = MonitorEntity.create(
                resourceId, request.type(), request.enabled(), request.intervalSeconds(), clock.instant(),
                request.storageMode(), request.retentionDays(),
                thresholdOrDefault(request.failureThreshold(), MonitorEntity.DEFAULT_FAILURE_THRESHOLD),
                thresholdOrDefault(request.recoveryThreshold(), MonitorEntity.DEFAULT_RECOVERY_THRESHOLD)
        );
        try {
            MonitorEntity saved = monitorRepository.saveAndFlush(monitor);
            resourceHealthService.recalculate(resourceId);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw monitorConflict();
        }
    }

    @Transactional(readOnly = true)
    public List<MonitorEntity> list(long resourceId, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        return monitorRepository.findAllByResourceIdOrderById(resourceId);
    }

    @Transactional
    public MonitorEntity update(
            long id,
            UpdateMonitorRequest request,
            long currentUserId
    ) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        if (monitor.storageMode() == StorageMode.HISTORY && request.storageMode() == StorageMode.LATEST_ONLY) {
            resultRepository.deleteAllByMonitorId(id);
        }
        monitor.update(
                request.enabled(), request.intervalSeconds(), request.storageMode(), request.retentionDays(),
                thresholdOrDefault(request.failureThreshold(), MonitorEntity.DEFAULT_FAILURE_THRESHOLD),
                thresholdOrDefault(request.recoveryThreshold(), MonitorEntity.DEFAULT_RECOVERY_THRESHOLD),
                clock.instant()
        );
        monitorRepository.flush();
        resourceHealthService.recalculate(monitor.resourceId());
        return monitor;
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        monitorRepository.delete(monitor);
        monitorRepository.flush();
        resourceHealthService.recalculate(monitor.resourceId());
    }

    public MonitorEntity run(long id, long currentUserId) {
        MonitorEntity monitor = monitorRepository.findById(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        executionService.execute(id);
        return monitorRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public SearchResult<MonitoringResultEntity> searchResults(
            long monitorId,
            SearchQuery query,
            long currentUserId
    ) {
        MonitorEntity monitor = monitorRepository.findById(monitorId).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        if (monitor.storageMode() != StorageMode.HISTORY) {
            throw new ConflictException(
                    "MONITOR_HISTORY_NOT_ENABLED", "History is available only for HISTORY storage mode"
            );
        }
        return searchService.search(
                query,
                JpaSearchScopes.equal(MonitoringResultEntity_.monitorId, monitorId),
                MonitoringResultSearchDefinition.DEFINITION
        );
    }

    private void requireSupported(ResourceEntity resource, ProbeType type) {
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        if (!handlerRegistry.supports(type, config)) {
            throw new ConflictException(
                    "MONITOR_TYPE_NOT_SUPPORTED",
                    "Probe type %s is not supported for resource type %s".formatted(type, resource.type())
            );
        }
    }

    private ConflictException monitorConflict() {
        return new ConflictException(
                "MONITOR_ALREADY_EXISTS", "A monitor of this type already exists for the resource"
        );
    }

    private int thresholdOrDefault(Integer threshold, int defaultValue) {
        return threshold == null ? defaultValue : threshold;
    }
}
