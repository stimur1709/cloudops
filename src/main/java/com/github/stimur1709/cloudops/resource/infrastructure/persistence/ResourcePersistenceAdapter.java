package com.github.stimur1709.cloudops.resource.infrastructure.persistence;

import java.util.Optional;

import com.github.stimur1709.cloudops.resource.domain.Resource;
import com.github.stimur1709.cloudops.resource.domain.ResourceRepository;
import org.springframework.stereotype.Repository;

@Repository
class ResourcePersistenceAdapter implements ResourceRepository {

    private final ResourceJpaRepository jpaRepository;

    ResourcePersistenceAdapter(ResourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Resource save(Resource resource) {
        ResourceEntity savedResource = jpaRepository.saveAndFlush(ResourceEntity.from(resource));
        return savedResource.toDomain();
    }

    @Override
    public Optional<Resource> findById(long id) {
        return jpaRepository.findById(id).map(ResourceEntity::toDomain);
    }
}

