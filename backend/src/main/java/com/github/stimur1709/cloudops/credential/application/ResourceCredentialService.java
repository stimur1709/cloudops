package com.github.stimur1709.cloudops.credential.application;

import com.github.stimur1709.cloudops.common.application.BadRequestException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.CredentialType;
import com.github.stimur1709.cloudops.credential.api.CredentialResponse;
import com.github.stimur1709.cloudops.credential.api.ResourceCredentialResponse;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialEntity;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialJpaRepository;
import com.github.stimur1709.cloudops.credential.persistence.CredentialEntity;
import com.github.stimur1709.cloudops.credential.persistence.CredentialJpaRepository;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceCredentialService {
    private final ResourceCredentialJpaRepository repository;
    private final ResourceJpaRepository resourceRepository;
    private final CredentialJpaRepository credentialRepository;
    private final OrganizationAuthorization authorization;

    public ResourceCredentialService(
            ResourceCredentialJpaRepository repository,
            ResourceJpaRepository resourceRepository,
            CredentialJpaRepository credentialRepository,
            OrganizationAuthorization authorization) {
        this.repository = repository;
        this.resourceRepository = resourceRepository;
        this.credentialRepository = credentialRepository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<ResourceCredentialResponse> getAll(long resourceId, long userId) {
        accessibleResource(resourceId, userId, false);
        return repository.findDetailsByResourceIdOrderByPurpose(resourceId).stream()
                .map(details -> new ResourceCredentialResponse(
                        details.purpose(),
                        new CredentialResponse(
                                details.credentialId(),
                                details.organizationId(),
                                details.name(),
                                details.type(),
                                details.username(),
                                details.createdAt(),
                                details.updatedAt())))
                .toList();
    }

    @Transactional
    public ResourceCredentialResponse bind(long resourceId, CredentialPurpose purpose, long credentialId, long userId) {
        ResourceEntity resource = accessibleResource(resourceId, userId, true);
        CredentialEntity credential =
                credentialRepository.findById(credentialId).orElseThrow(NotFoundException::new);
        if (!resource.organizationId().equals(credential.organizationId())) throw new NotFoundException();
        requireCompatible(purpose, credential.type());
        ResourceCredentialEntity binding = repository
                .findByResourceIdAndPurpose(resourceId, purpose)
                .orElseGet(() -> new ResourceCredentialEntity(resourceId, purpose, credentialId));
        binding.replace(credentialId);
        repository.saveAndFlush(binding);
        return new ResourceCredentialResponse(purpose, CredentialResponse.from(credential));
    }

    @Transactional
    public void unbind(long resourceId, CredentialPurpose purpose, long userId) {
        accessibleResource(resourceId, userId, true);
        repository.findByResourceIdAndPurpose(resourceId, purpose).ifPresent(repository::delete);
    }

    private ResourceEntity accessibleResource(long resourceId, long userId, boolean manager) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        if (manager) authorization.requireManager(resource.organizationId(), userId);
        else authorization.requireMember(resource.organizationId(), userId);
        return resource;
    }

    private void requireCompatible(CredentialPurpose purpose, CredentialType type) {
        boolean compatible = purpose == CredentialPurpose.SSH || type == CredentialType.USERNAME_PASSWORD;
        if (!compatible) {
            throw new BadRequestException(
                    "INCOMPATIBLE_CREDENTIAL", "Credential type is not compatible with the requested purpose");
        }
    }
}
