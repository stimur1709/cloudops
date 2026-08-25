package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.infrastructure.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.infrastructure.persistence.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private final ResourceJpaRepository resourceRepository;
    private final Clock clock;

    public ResourceService(ResourceJpaRepository resourceRepository, Clock clock) {
        this.resourceRepository = resourceRepository;
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
}
