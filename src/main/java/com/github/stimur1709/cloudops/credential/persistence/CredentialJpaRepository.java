package com.github.stimur1709.cloudops.credential.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialJpaRepository extends JpaRepository<CredentialEntity, Long> {
    boolean existsByOrganizationIdAndName(long organizationId, String name);
    boolean existsByOrganizationIdAndNameAndIdNot(long organizationId, String name, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from CredentialEntity credential where credential.id = :id")
    Optional<CredentialEntity> findByIdForUpdate(@Param("id") long id);
}
