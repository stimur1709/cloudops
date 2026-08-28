package com.github.stimur1709.cloudops.monitoring.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceHealthJpaRepository extends JpaRepository<ResourceHealthEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select health from ResourceHealthEntity health where health.resourceId = :resourceId")
    Optional<ResourceHealthEntity> findByResourceIdForUpdate(@Param("resourceId") long resourceId);
}
