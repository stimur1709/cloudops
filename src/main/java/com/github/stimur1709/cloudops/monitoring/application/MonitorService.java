package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScopes;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.persistence.*;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {
    private final MonitorJpaRepository monitorRepository;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final JpaSearchService searchService;
    private final MonitoringSettingsResolver settingsResolver;
    private final Clock clock;

    public MonitorService(
            MonitorJpaRepository monitorRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            JpaSearchService searchService,
            MonitoringSettingsResolver settingsResolver,
            Clock clock) {
        this.monitorRepository = monitorRepository;
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.searchService = searchService;
        this.settingsResolver = settingsResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MonitorEntity> list(long resourceId, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        return monitorRepository.findAllByResourceIdOrderById(resourceId);
    }

    @Transactional
    public void scheduleRun(long id, long currentUserId) {
        MonitorEntity monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        ResourceEntity resource =
                resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        if (!monitor.compatible()) {
            throw new ConflictException("MONITOR_INCOMPATIBLE", "An incompatible monitor cannot be scheduled");
        }
        if (!settingsResolver.resolve(resource, monitor.type()).enabled()) {
            throw new ConflictException("MONITOR_DISABLED", "A disabled monitor cannot be scheduled");
        }
        monitor.scheduleNow(clock.instant());
    }

    @Transactional(readOnly = true)
    public SearchResult<MonitoringResultEntity> searchResults(long monitorId, SearchQuery query, long currentUserId) {
        MonitorEntity monitor = monitorRepository.findById(monitorId).orElseThrow(NotFoundException::new);
        ResourceEntity resource =
                resourceRepository.findById(monitor.resourceId()).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        if (settingsResolver.resolve(resource, monitor.type()).storageMode() != StorageMode.HISTORY) {
            throw new ConflictException(
                    "MONITOR_HISTORY_NOT_ENABLED", "History is available only for HISTORY storage mode");
        }
        return searchService.search(
                query,
                JpaSearchScopes.equal(MonitoringResultEntity_.monitorId, monitorId),
                MonitoringResultSearchDefinition.DEFINITION);
    }
}
