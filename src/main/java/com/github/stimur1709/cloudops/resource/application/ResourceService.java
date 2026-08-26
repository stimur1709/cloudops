package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity_;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity_;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceSearchDefinition;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    public ResourceService(
            ResourceJpaRepository resourceRepository,
            JpaSearchService searchService,
            OrganizationJpaRepository organizationRepository,
            OrganizationAuthorization authorization,
            Clock clock
    ) {
        this.resourceRepository = resourceRepository;
        this.searchService = searchService;
        this.organizationRepository = organizationRepository;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public ResourceEntity create(
            String name,
            ResourceType type,
            ResourceStatus status,
            long organizationId,
            long currentUserId
    ) {
        OrganizationEntity organization = getOrganizationForUpdate(organizationId);
        authorization.requireManager(organizationId, currentUserId, "Organization");
        if (resourceRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw resourceNameConflict();
        }
        Instant now = clock.instant();
        ResourceEntity resource = ResourceEntity.create(name, type, status, organization, now);
        return save(resource);
    }

    @Transactional(readOnly = true)
    public ResourceEntity get(long id, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
        authorization.requireMember(resource.organizationId(), currentUserId, "Resource");
        return resource;
    }

    @Transactional(readOnly = true)
    public SearchResult<ResourceEntity> search(SearchQuery search, long currentUserId) {
        return searchService.search(search, (root, query, builder) -> {
            Subquery<Long> memberships = query.subquery(Long.class);
            Root<OrganizationMembershipEntity> membership = memberships.from(OrganizationMembershipEntity.class);
            memberships.select(membership.get(OrganizationMembershipEntity_.organizationId));
            memberships.where(builder.equal(
                    membership.get(OrganizationMembershipEntity_.userId), currentUserId
            ));
            return root.get(ResourceEntity_.organizationId).in(memberships);
        }, ResourceSearchDefinition.DEFINITION);
    }

    @Transactional
    public ResourceEntity update(
            long id,
            String name,
            ResourceType type,
            ResourceStatus status,
            long organizationId,
            long currentUserId
    ) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
        long sourceOrganizationId = resource.organizationId();
        authorization.requireManager(sourceOrganizationId, currentUserId, "Resource");
        OrganizationEntity organization = lockOrganizations(sourceOrganizationId, organizationId);
        authorization.requireManager(sourceOrganizationId, currentUserId, "Resource");
        if (organizationId != sourceOrganizationId) {
            authorization.requireManager(organizationId, currentUserId, "Organization");
        }
        if (resourceRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, id)) {
            throw resourceNameConflict();
        }
        resource.update(name, type, status, organization, clock.instant());
        return save(resource);
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        ResourceEntity resource = resourceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Resource"));
        getOrganizationForUpdate(resource.organizationId());
        authorization.requireManager(resource.organizationId(), currentUserId, "Resource");
        resourceRepository.delete(resource);
    }

    private OrganizationEntity getOrganizationForUpdate(long organizationId) {
        return organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization"));
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
