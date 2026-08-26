package com.github.stimur1709.cloudops.organization.application;

import java.time.Clock;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity_;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity_;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationSearchDefinition;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationJpaRepository organizationRepository;
    private final JpaSearchService searchService;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationMembershipJpaRepository membershipRepository;
    private final UserJpaRepository userRepository;
    private final OrganizationAuthorization authorization;
    private final Clock clock;

    public OrganizationService(
            OrganizationJpaRepository organizationRepository,
            JpaSearchService searchService,
            ResourceJpaRepository resourceRepository,
            OrganizationMembershipJpaRepository membershipRepository,
            UserJpaRepository userRepository,
            OrganizationAuthorization authorization,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.searchService = searchService;
        this.resourceRepository = resourceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public OrganizationEntity create(String name, long currentUserId) {
        UserEntity creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("User"));
        OrganizationEntity organization = organizationRepository.saveAndFlush(
                OrganizationEntity.create(name, clock.instant())
        );
        membershipRepository.save(OrganizationMembershipEntity.create(
                organization, creator, MembershipRole.OWNER, clock.instant()
        ));
        return organization;
    }

    @Transactional(readOnly = true)
    public OrganizationEntity get(long id, long currentUserId) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
        authorization.requireMember(id, currentUserId, "Organization");
        return organization;
    }

    @Transactional(readOnly = true)
    public SearchResult<OrganizationEntity> search(SearchQuery search, long currentUserId) {
        return searchService.search(search, (root, query, builder) -> {
            Subquery<Long> memberships = query.subquery(Long.class);
            Root<OrganizationMembershipEntity> membership = memberships.from(OrganizationMembershipEntity.class);
            memberships.select(membership.get(OrganizationMembershipEntity_.organizationId));
            memberships.where(builder.equal(
                    membership.get(OrganizationMembershipEntity_.userId), currentUserId
            ));
            return root.get(OrganizationEntity_.id).in(memberships);
        }, OrganizationSearchDefinition.DEFINITION);
    }

    @Transactional
    public OrganizationEntity update(long id, String name, long currentUserId) {
        OrganizationEntity organization = organizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
        authorization.requireManager(id, currentUserId, "Organization");
        organization.update(name, clock.instant());
        return organization;
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        OrganizationEntity organization = organizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
        authorization.requireOwner(id, currentUserId, "Organization");
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
