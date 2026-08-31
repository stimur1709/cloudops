package com.github.stimur1709.cloudops.resource.persistence;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, Long> {

    boolean existsByOrganizationIdAndName(long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(long organizationId, String name, long id);

    boolean existsByOrganizationId(long organizationId);

    List<ResourceEntity> findAllByOrganizationId(long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resource from ResourceEntity resource where resource.id = :id")
    Optional<ResourceEntity> findByIdForUpdate(@Param("id") long id);
}
