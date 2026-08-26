package com.github.stimur1709.cloudops.organization.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationSearchRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationJpaRepository organizationRepository;
    private final OrganizationSearchRepository organizationSearchRepository;
    private final ResourceJpaRepository resourceRepository;
    private final Clock clock;

    public OrganizationService(
            OrganizationJpaRepository organizationRepository,
            OrganizationSearchRepository organizationSearchRepository,
            ResourceJpaRepository resourceRepository,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationSearchRepository = organizationSearchRepository;
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    @Transactional
    public OrganizationEntity create(String name) {
        return organizationRepository.save(OrganizationEntity.create(name, clock.instant()));
    }

    @Transactional(readOnly = true)
    public OrganizationEntity get(long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public SearchResult<OrganizationEntity> search(SearchQuery search) {
        return organizationSearchRepository.search(search);
    }

    @Transactional
    public OrganizationEntity update(long id, String name) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
        Instant now = clock.instant();
        Instant updatedAt = now.isAfter(organization.updatedAt())
                ? now
                : organization.updatedAt().plusNanos(1_000);
        organization.update(name, updatedAt);
        return organization;
    }

    @Transactional
    public void delete(long id) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
        if (resourceRepository.existsByOrganizationId(id)) {
            throw new OrganizationInUseException();
        }
        try {
            organizationRepository.delete(organization);
            organizationRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationInUseException();
        }
    }
}
