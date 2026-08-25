package com.github.stimur1709.cloudops.resource.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, Long> {
}
