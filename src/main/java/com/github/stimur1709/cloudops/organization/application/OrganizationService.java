package com.github.stimur1709.cloudops.organization.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationSearchDefinition;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationJpaRepository organizationRepository;
    private final JpaSearchService searchService;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationMembershipJpaRepository membershipRepository;
    private final Clock clock;

    public OrganizationService(
            OrganizationJpaRepository organizationRepository,
            JpaSearchService searchService,
            ResourceJpaRepository resourceRepository,
            OrganizationMembershipJpaRepository membershipRepository,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.searchService = searchService;
        this.resourceRepository = resourceRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
    }

    @Transactional
    public OrganizationEntity create(String name) {
        return organizationRepository.save(OrganizationEntity.create(name, clock.instant()));
    }

    @Transactional(readOnly = true)
    public OrganizationEntity get(long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
    }

    @Transactional(readOnly = true)
    public SearchResult<OrganizationEntity> search(SearchQuery search) {
        return searchService.search(search, OrganizationSearchDefinition.DEFINITION);
    }

    @Transactional
    public OrganizationEntity update(long id, String name) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
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
                .orElseThrow(() -> new NotFoundException("Organization"));
        if (resourceRepository.existsByOrganizationId(id) || membershipRepository.existsByOrganizationId(id)) {
            throw organizationInUse();
        }
        try {
            organizationRepository.delete(organization);
            organizationRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw organizationInUse();
        }
    }

    private ConflictException organizationInUse() {
        return new ConflictException(
                "ORGANIZATION_IN_USE",
                "Organization cannot be deleted while it has resources or members"
        );
    }
}
