package com.github.stimur1709.cloudops.credential.binding;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceCredentialJpaRepository extends JpaRepository<ResourceCredentialEntity, ResourceCredentialId> {

    @Query("""
            SELECT binding.purpose, credential.id, credential.organizationId, credential.name,
                credential.type, credential.username, credential.createdAt, credential.updatedAt
            FROM ResourceCredentialEntity binding
            JOIN CredentialEntity credential
              ON credential.id = binding.credentialId
            WHERE binding.resourceId = :resourceId
            ORDER BY binding.purpose
            """)
    List<ResourceCredentialDetails> findDetailsByResourceIdOrderByPurpose(@Param("resourceId") long resourceId);

    Optional<ResourceCredentialEntity> findByResourceIdAndPurpose(long resourceId, CredentialPurpose purpose);

    boolean existsByResourceIdAndPurpose(long resourceId, CredentialPurpose purpose);

    List<ResourceCredentialEntity> findAllByCredentialId(long credentialId);

    boolean existsByCredentialId(long credentialId);
}
