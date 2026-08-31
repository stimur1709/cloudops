package com.github.stimur1709.cloudops.credential.binding;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceCredentialJpaRepository extends JpaRepository<ResourceCredentialEntity, ResourceCredentialId> {

    @Query("""
            select binding.purpose, credential.id, credential.organizationId, credential.name,
                credential.type, credential.username, credential.createdAt, credential.updatedAt
            from ResourceCredentialEntity binding
            join CredentialEntity credential on credential.id = binding.credentialId
            where binding.resourceId = :resourceId
            order by binding.purpose
            """)
    List<ResourceCredentialDetails> findDetailsByResourceIdOrderByPurpose(@Param("resourceId") long resourceId);

    Optional<ResourceCredentialEntity> findByResourceIdAndPurpose(long resourceId, CredentialPurpose purpose);

    List<ResourceCredentialEntity> findAllByCredentialId(long credentialId);

    boolean existsByCredentialId(long credentialId);
}
