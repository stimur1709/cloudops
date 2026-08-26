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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private final ResourceJpaRepository resourceRepository;
    private final ResourceSearchRepository resourceSearchRepository;
    private final Clock clock;

    public ResourceService(
            ResourceJpaRepository resourceRepository,
            ResourceSearchRepository resourceSearchRepository,
            Clock clock
    ) {
        this.resourceRepository = resourceRepository;
        this.resourceSearchRepository = resourceSearchRepository;
        this.clock = clock;
    }

    @Transactional
    public ResourceEntity create(String name, ResourceType type, ResourceStatus status) {
        Instant now = clock.instant();
        ResourceEntity resource = ResourceEntity.create(name, type, status, now);
        return resourceRepository.save(resource);
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
            ResourceStatus status
    ) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        Instant now = clock.instant();
        Instant updatedAt = now.isAfter(resource.updatedAt())
                ? now
                : resource.updatedAt().plusNanos(1_000);
        resource.update(name, type, status, updatedAt);
        return resource;
    }

    @Transactional
    public void delete(long id) {
        ResourceEntity resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        resourceRepository.delete(resource);
    }
}
