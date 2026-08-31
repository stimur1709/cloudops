package com.github.stimur1709.cloudops.credential.application;

import com.github.stimur1709.cloudops.common.application.BadRequestException;
import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.CredentialType;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialJpaRepository;
import com.github.stimur1709.cloudops.credential.crypto.SecretCryptoService;
import com.github.stimur1709.cloudops.credential.persistence.CredentialEntity;
import com.github.stimur1709.cloudops.credential.persistence.CredentialJpaRepository;
import com.github.stimur1709.cloudops.credential.persistence.CredentialSearchDefinition;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipScopes;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialService {
    private final CredentialJpaRepository repository;
    private final ResourceCredentialJpaRepository bindingRepository;
    private final OrganizationJpaRepository organizationRepository;
    private final OrganizationAuthorization authorization;
    private final JpaSearchService searchService;
    private final SecretCryptoService cryptoService;
    private final Clock clock;

    public CredentialService(
            CredentialJpaRepository repository,
            ResourceCredentialJpaRepository bindingRepository,
            OrganizationJpaRepository organizationRepository,
            OrganizationAuthorization authorization,
            JpaSearchService searchService,
            SecretCryptoService cryptoService,
            Clock clock) {
        this.repository = repository;
        this.bindingRepository = bindingRepository;
        this.organizationRepository = organizationRepository;
        this.authorization = authorization;
        this.searchService = searchService;
        this.cryptoService = cryptoService;
        this.clock = clock;
    }

    @Transactional
    public CredentialEntity create(
            long organizationId, String name, CredentialType type, String username, String secret, long userId) {
        OrganizationEntity organization =
                organizationRepository.findByIdForUpdate(organizationId).orElseThrow(NotFoundException::new);
        authorization.requireManager(organizationId, userId);
        if (repository.existsByOrganizationIdAndName(organizationId, name)) throw nameConflict();
        return save(CredentialEntity.create(
                organization, name, type, username, cryptoService.encrypt(secret), clock.instant()));
    }

    @Transactional(readOnly = true)
    public CredentialEntity get(long id, long userId) {
        CredentialEntity entity = repository.findById(id).orElseThrow(NotFoundException::new);
        authorization.requireMember(entity.organizationId(), userId);
        return entity;
    }

    @Transactional(readOnly = true)
    public SearchResult<CredentialEntity> search(SearchQuery query, long userId) {
        return searchService.search(
                query,
                OrganizationMembershipScopes.visibleTo(userId, root -> root.get("organizationId")),
                CredentialSearchDefinition.DEFINITION);
    }

    @Transactional
    public CredentialEntity update(
            long id, String name, CredentialType type, String username, String secret, long userId) {
        CredentialEntity entity = repository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        authorization.requireManager(entity.organizationId(), userId);
        if (repository.existsByOrganizationIdAndNameAndIdNot(entity.organizationId(), name, id)) {
            throw nameConflict();
        }
        if (type == CredentialType.SSH_PRIVATE_KEY
                && bindingRepository.findAllByCredentialId(id).stream()
                        .anyMatch(binding -> binding.purpose() == CredentialPurpose.DATABASE)) {
            throw new BadRequestException(
                    "INCOMPATIBLE_CREDENTIAL", "Credential type is not compatible with an existing resource binding");
        }
        entity.update(name, type, username, cryptoService.encrypt(secret), clock.instant());
        return save(entity);
    }

    @Transactional
    public void delete(long id, long userId) {
        CredentialEntity entity = repository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
        authorization.requireManager(entity.organizationId(), userId);
        if (bindingRepository.existsByCredentialId(id)) {
            throw new ConflictException("CREDENTIAL_IN_USE", "Credential is used by a resource");
        }
        repository.delete(entity);
    }

    private CredentialEntity save(CredentialEntity entity) {
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw nameConflict();
        }
    }

    private ConflictException nameConflict() {
        return new ConflictException(
                "CREDENTIAL_NAME_CONFLICT", "Credential name is already used in this organization");
    }
}
