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
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity_;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultJpaRepository;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultSearchDefinition;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
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
    private final MonitoringProperties properties;
    private final JpaSearchService searchService;
    private final Clock clock;

    public MonitorService(
            MonitorJpaRepository monitorRepository,
            MonitoringResultJpaRepository resultRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            MonitoringProperties properties,
            JpaSearchService searchService,
            Clock clock
    ) {
        this.monitorRepository = monitorRepository;
        this.resultRepository = resultRepository;
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.properties = properties;
        this.searchService = searchService;
        this.clock = clock;
    }

    @Transactional
    public MonitorEntity create(
            long resourceId,
            ProbeType type,
            int intervalSeconds,
            boolean enabled,
            StorageMode storageMode,
            Integer retentionDays,
            long currentUserId
    ) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(resourceId)
                .orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        validateSettings(intervalSeconds, storageMode, retentionDays);
        requireSupported(resource, type);
        if (resource.status() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_INACTIVE", "Monitoring requires an active resource");
        }
        if (monitorRepository.existsByResourceIdAndType(resourceId, type)) {
            throw monitorConflict();
        }
        MonitorEntity monitor = MonitorEntity.create(
                resourceId, type, enabled, intervalSeconds, clock.instant(), storageMode, retentionDays
        );
        try {
            return monitorRepository.saveAndFlush(monitor);
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
            int intervalSeconds,
            boolean enabled,
            StorageMode storageMode,
            Integer retentionDays,
            long currentUserId
    ) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        validateSettings(intervalSeconds, storageMode, retentionDays);
        if (monitor.storageMode() == StorageMode.HISTORY && storageMode == StorageMode.LATEST_ONLY) {
            resultRepository.deleteAllByMonitorId(id);
        }
        monitor.update(enabled, intervalSeconds, storageMode, retentionDays, clock.instant());
        return monitor;
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireManager(resource.organizationId(), currentUserId);
        monitorRepository.delete(monitor);
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
        if (!type.supports(resource.type())) {
            throw new ConflictException(
                    "MONITOR_TYPE_NOT_SUPPORTED",
                    "Probe type %s is not supported for resource type %s".formatted(type, resource.type())
            );
        }
    }

    private void validateSettings(int intervalSeconds, StorageMode storageMode, Integer retentionDays) {
        if (intervalSeconds < properties.minimumIntervalSeconds()) {
            throw new InvalidMonitorConfigurationException(
                    "intervalSeconds",
                    "Interval must be at least %d seconds".formatted(properties.minimumIntervalSeconds())
            );
        }
        if (storageMode == StorageMode.HISTORY && (retentionDays == null || retentionDays < 1 || retentionDays > 365)) {
            throw new InvalidMonitorConfigurationException(
                    "retentionDays", "Retention days must be between 1 and 365 for HISTORY"
            );
        }
        if (storageMode == StorageMode.LATEST_ONLY && retentionDays != null) {
            throw new InvalidMonitorConfigurationException(
                    "retentionDays", "Retention days must be null for LATEST_ONLY"
            );
        }
    }

    private ConflictException monitorConflict() {
        return new ConflictException(
                "MONITOR_ALREADY_EXISTS", "A monitor of this type already exists for the resource"
        );
    }
}
