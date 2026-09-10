package com.github.stimur1709.cloudops.organization.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT organization FROM OrganizationEntity organization WHERE organization.id = :id")
    Optional<OrganizationEntity> findByIdForUpdate(@Param("id") long id);
}
