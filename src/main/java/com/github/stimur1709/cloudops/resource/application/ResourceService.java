package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
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
    private final Clock clock;

    public ResourceService(
            ResourceJpaRepository resourceRepository,
            JpaSearchService searchService,
            OrganizationJpaRepository organizationRepository,
            Clock clock
    ) {
        this.resourceRepository = resourceRepository;
        this.searchService = searchService;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Transactional
    public ResourceEntity create(String name, ResourceType type, ResourceStatus status, long organizationId) {
        OrganizationEntity organization = getOrganization(organizationId);
        if (resourceRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw resourceNameConflict();
        }
        Instant now = clock.instant();
        ResourceEntity resource = ResourceEntity.create(name, type, status, organization, now);
        return save(resource);
    }

    @Transactional(readOnly = true)
    public ResourceEntity get(long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
    }

    @Transactional(readOnly = true)
    public SearchResult<ResourceEntity> search(SearchQuery search) {
        return searchService.search(search, ResourceSearchDefinition.DEFINITION);
    }

    @Transactional
    public ResourceEntity update(
            long id,
            String name,
            ResourceType type,
            ResourceStatus status,
            long organizationId
    ) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
        OrganizationEntity organization = getOrganization(organizationId);
        if (resourceRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id)) {
            throw resourceNameConflict();
        }
        resource.update(name, type, status, organization, clock.instant());
        return save(resource);
    }

    @Transactional
    public void delete(long id) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
        resourceRepository.delete(resource);
    }

    private OrganizationEntity getOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization"));
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
