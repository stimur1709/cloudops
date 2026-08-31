package com.github.stimur1709.cloudops.credential.binding;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCredentialJpaRepository extends JpaRepository<ResourceCredentialEntity, ResourceCredentialId> {

    List<ResourceCredentialEntity> findAllByResourceIdOrderByPurpose(long resourceId);

    Optional<ResourceCredentialEntity> findByResourceIdAndPurpose(long resourceId, CredentialPurpose purpose);

    List<ResourceCredentialEntity> findAllByCredentialId(long credentialId);

    boolean existsByCredentialId(long credentialId);
}
