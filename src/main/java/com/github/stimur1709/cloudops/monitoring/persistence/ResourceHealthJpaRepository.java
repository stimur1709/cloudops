package com.github.stimur1709.cloudops.monitoring.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceHealthJpaRepository extends JpaRepository<ResourceHealthEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT health FROM ResourceHealthEntity health WHERE health.resourceId = :resourceId")
    Optional<ResourceHealthEntity> findByResourceIdForUpdate(@Param("resourceId") long resourceId);
}
