package com.github.stimur1709.cloudops.resource.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, Long> {

    boolean existsByOrganizationIdAndName(long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(long organizationId, String name, long id);

    boolean existsByOrganizationId(long organizationId);
}
