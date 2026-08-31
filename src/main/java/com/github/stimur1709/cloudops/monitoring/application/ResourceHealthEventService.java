package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScopes;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity_;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventSearchDefinition;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceHealthEventService {

    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final JpaSearchService searchService;

    public ResourceHealthEventService(
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            JpaSearchService searchService) {
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.searchService = searchService;
    }

    @Transactional(readOnly = true)
    public SearchResult<ResourceHealthEventEntity> search(long resourceId, SearchQuery query, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        return searchService.search(
                query,
                JpaSearchScopes.equal(ResourceHealthEventEntity_.resourceId, resourceId),
                ResourceHealthEventSearchDefinition.DEFINITION);
    }
}
