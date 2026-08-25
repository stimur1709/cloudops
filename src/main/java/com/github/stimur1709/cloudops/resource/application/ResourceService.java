package com.github.stimur1709.cloudops.resource.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.resource.domain.Resource;
import com.github.stimur1709.cloudops.resource.domain.ResourceRepository;
import com.github.stimur1709.cloudops.resource.domain.ResourceStatus;
import com.github.stimur1709.cloudops.resource.domain.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final Clock clock;

    public ResourceService(ResourceRepository resourceRepository, Clock clock) {
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    @Transactional
    public Resource create(String name, ResourceType type, ResourceStatus status) {
        Instant now = clock.instant();
        Resource resource = new Resource(null, name, type, status, now, now);
        return resourceRepository.save(resource);
    }

    @Transactional(readOnly = true)
    public Resource get(long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }
}

