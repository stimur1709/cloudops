package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceSearchRepository;
import com.github.stimur1709.cloudops.organization.application.OrganizationNotFoundException;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class ResourceService {

    private final ResourceJpaRepository resourceRepository;
    private final ResourceSearchRepository resourceSearchRepository;
    private final OrganizationJpaRepository organizationRepository;
    private final Clock clock;

    public ResourceService(
            ResourceJpaRepository resourceRepository,
            ResourceSearchRepository resourceSearchRepository,
            OrganizationJpaRepository organizationRepository,
            Clock clock
    ) {
        this.resourceRepository = resourceRepository;
        this.resourceSearchRepository = resourceSearchRepository;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Transactional
    public ResourceEntity create(String name, ResourceType type, ResourceStatus status, long organizationId) {
        OrganizationEntity organization = getOrganization(organizationId);
        if (resourceRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new ResourceNameConflictException();
        }
        Instant now = clock.instant();
        ResourceEntity resource = ResourceEntity.create(name, type, status, organization, now);
        return save(resource);
    }

    @Transactional(readOnly = true)
    public ResourceEntity get(long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public SearchResult<ResourceEntity> search(SearchQuery search) {
        return resourceSearchRepository.search(search);
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
                .orElseThrow(() -> new ResourceNotFoundException(id));
        OrganizationEntity organization = getOrganization(organizationId);
        if (resourceRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id)) {
            throw new ResourceNameConflictException();
        }
        Instant now = clock.instant();
        Instant updatedAt = now.isAfter(resource.updatedAt())
                ? now
                : resource.updatedAt().plusNanos(1_000);
        resource.update(name, type, status, organization, updatedAt);
        return save(resource);
    }

    @Transactional
    public void delete(long id) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        resourceRepository.delete(resource);
    }

    private OrganizationEntity getOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    private ResourceEntity save(ResourceEntity resource) {
        try {
            return resourceRepository.saveAndFlush(resource);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceNameConflictException();
        }
    }
}
