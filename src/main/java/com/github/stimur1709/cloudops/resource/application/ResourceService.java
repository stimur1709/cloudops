package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipScopes;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity_;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceSearchDefinition;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class ResourceService {

    private final ResourceJpaRepository resourceRepository;
    private final JpaSearchService searchService;
    private final OrganizationJpaRepository organizationRepository;
    private final OrganizationAuthorization authorization;
    private final Clock clock;
    private final ResourceConfigMapper configMapper;

    public ResourceService(
            ResourceJpaRepository resourceRepository,
            JpaSearchService searchService,
            OrganizationJpaRepository organizationRepository,
            OrganizationAuthorization authorization,
            Clock clock,
            ResourceConfigMapper configMapper
    ) {
        this.resourceRepository = resourceRepository;
        this.searchService = searchService;
        this.organizationRepository = organizationRepository;
        this.authorization = authorization;
        this.clock = clock;
        this.configMapper = configMapper;
    }

    @Transactional
    public ResourceEntity create(
            String name,
            ResourceType type,
            ResourceStatus status,
            long organizationId,
            ResourceConfig config,
            long currentUserId
    ) {
        OrganizationEntity organization = getOrganizationForUpdate(organizationId);
        authorization.requireManager(organizationId, currentUserId);
        if (resourceRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw resourceNameConflict();
        }
        Instant now = clock.instant();
        ResourceEntity resource = ResourceEntity.create(
                name, type, status, organization, configMapper.toJson(config), now
        );
        return save(resource);
    }

    @Transactional(readOnly = true)
    public ResourceEntity get(long id, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        return resource;
    }

    @Transactional(readOnly = true)
    public SearchResult<ResourceEntity> search(SearchQuery search, long currentUserId) {
        return searchService.search(
                search,
                OrganizationMembershipScopes.visibleTo(currentUserId, ResourceEntity_.organizationId),
                ResourceSearchDefinition.DEFINITION
        );
    }

    @Transactional
    public ResourceEntity update(
            long id,
            String name,
            ResourceType type,
            ResourceStatus status,
            long organizationId,
            ResourceConfig config,
            long currentUserId
    ) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(id)
                .orElseThrow(NotFoundException::new);
        long sourceOrganizationId = resource.organizationId();
        authorization.requireManager(sourceOrganizationId, currentUserId);
        OrganizationEntity organization = lockOrganizations(sourceOrganizationId, organizationId);
        authorization.requireManager(sourceOrganizationId, currentUserId);
        if (organizationId != sourceOrganizationId) {
            authorization.requireManager(organizationId, currentUserId);
        }
        if (resourceRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id)) {
            throw resourceNameConflict();
        }
        resource.update(name, type, status, organization, configMapper.toJson(config), clock.instant());
        return save(resource);
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(id)
                .orElseThrow(NotFoundException::new);
        getOrganizationForUpdate(resource.organizationId());
        authorization.requireManager(resource.organizationId(), currentUserId);
        resourceRepository.delete(resource);
    }

    private OrganizationEntity getOrganizationForUpdate(long organizationId) {
        return organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(NotFoundException::new);
    }

    private OrganizationEntity lockOrganizations(long sourceId, long targetId) {
        long firstId = Math.min(sourceId, targetId);
        long secondId = Math.max(sourceId, targetId);
        OrganizationEntity first = getOrganizationForUpdate(firstId);
        if (firstId == secondId) {
            return first;
        }
        OrganizationEntity second = getOrganizationForUpdate(secondId);
        return targetId == firstId ? first : second;
    }

    private ResourceEntity save(ResourceEntity resource) {
        try {
            return resourceRepository.saveAndFlush(resource);
        } catch (DataIntegrityViolationException exception) {
            throw resourceNameConflict();
        }
    }

    private ConflictException resourceNameConflict() {
        return new ConflictException(
                "RESOURCE_NAME_CONFLICT",
                "Resource name is already used in this organization"
        );
    }
}
