package com.github.stimur1709.cloudops.credential.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialJpaRepository extends JpaRepository<CredentialEntity, Long> {

    boolean existsByOrganizationIdAndName(long organizationId, String name);

    boolean existsByOrganizationIdAndNameAndIdNot(long organizationId, String name, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT credential FROM CredentialEntity credential WHERE credential.id = :id")
    Optional<CredentialEntity> findByIdForUpdate(@Param("id") long id);
}
