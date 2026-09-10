package com.github.stimur1709.cloudops.membership.application;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScopes;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity_;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipSearchDefinition;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMembershipService {

    private final OrganizationMembershipJpaRepository membershipRepository;
    private final OrganizationJpaRepository organizationRepository;
    private final UserJpaRepository userRepository;
    private final JpaSearchService searchService;
    private final Clock clock;

    public OrganizationMembershipService(
            OrganizationMembershipJpaRepository membershipRepository,
            OrganizationJpaRepository organizationRepository,
            UserJpaRepository userRepository,
            JpaSearchService searchService,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.searchService = searchService;
        this.clock = clock;
    }

    @Transactional
    public OrganizationMembershipEntity add(long organizationId, long userId, MembershipRole role, long currentUserId) {
        OrganizationEntity organization = getOrganizationForUpdate(organizationId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        OrganizationMembershipEntity actor = findActor(memberships, currentUserId);
        requireCanAdd(actor.role(), role);
        UserEntity user = getUser(userId);
        if (memberships.stream().anyMatch(item -> item.userId() == userId)) {
            throw membershipConflict();
        }
        try {
            return membershipRepository.saveAndFlush(
                    OrganizationMembershipEntity.create(organization, user, role, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw membershipConflict();
        }
    }

    @Transactional(readOnly = true)
    public SearchResult<OrganizationMembershipEntity> search(
            long organizationId, SearchQuery search, long currentUserId) {
        checkOrganization(organizationId);
        if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, currentUserId)) {
            throw new NotFoundException();
        }
        return searchService.search(
                search,
                JpaSearchScopes.equal(OrganizationMembershipEntity_.organizationId, organizationId),
                OrganizationMembershipSearchDefinition.DEFINITION);
    }

    @Transactional
    public OrganizationMembershipEntity updateRole(
            long organizationId, long userId, MembershipRole role, long currentUserId) {
        getOrganizationForUpdate(organizationId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        OrganizationMembershipEntity actor = findActor(memberships, currentUserId);
        OrganizationMembershipEntity membership = find(memberships, userId);
        if (actor.role() != MembershipRole.OWNER) {
            throw new ForbiddenException();
        }
        if (currentUserId == userId && rank(role) > rank(membership.role())) {
            throw new ForbiddenException();
        }
        if (membership.role() == MembershipRole.OWNER && role != MembershipRole.OWNER && ownerCount(memberships) == 1) {
            throw lastOwnerConflict();
        }
        membership.changeRole(role, clock.instant());
        return membership;
    }

    @Transactional
    public void remove(long organizationId, long userId, long currentUserId) {
        getOrganizationForUpdate(organizationId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        OrganizationMembershipEntity actor = findActor(memberships, currentUserId);
        OrganizationMembershipEntity membership = find(memberships, userId);
        if (actor.role() == MembershipRole.MEMBER
                || actor.role() == MembershipRole.ADMIN && membership.role() != MembershipRole.MEMBER) {
            throw new ForbiddenException();
        }
        if (membership.role() == MembershipRole.OWNER && ownerCount(memberships) == 1) {
            throw lastOwnerConflict();
        }
        membershipRepository.delete(membership);
    }

    private void checkOrganization(long id) {
        organizationRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    private OrganizationEntity getOrganizationForUpdate(long id) {
        return organizationRepository.findByIdForUpdate(id).orElseThrow(NotFoundException::new);
    }

    private UserEntity getUser(long id) {
        return userRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    private OrganizationMembershipEntity find(List<OrganizationMembershipEntity> memberships, long userId) {
        return memberships.stream()
                .filter(item -> item.userId() == userId)
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }

    private OrganizationMembershipEntity findActor(List<OrganizationMembershipEntity> memberships, long currentUserId) {
        return memberships.stream()
                .filter(item -> item.userId() == currentUserId)
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }

    private void requireCanAdd(MembershipRole actorRole, MembershipRole addedRole) {
        if (actorRole == MembershipRole.MEMBER
                || actorRole == MembershipRole.ADMIN && addedRole != MembershipRole.MEMBER) {
            throw new ForbiddenException();
        }
    }

    private int rank(MembershipRole role) {
        return switch (role) {
            case MEMBER -> 0;
            case ADMIN -> 1;
            case OWNER -> 2;
        };
    }

    private long ownerCount(List<OrganizationMembershipEntity> memberships) {
        return memberships.stream()
                .filter(item -> item.role() == MembershipRole.OWNER)
                .count();
    }

    private ConflictException membershipConflict() {
        return new ConflictException("MEMBERSHIP_CONFLICT", "User is already a member of this organization");
    }

    private ConflictException lastOwnerConflict() {
        return new ConflictException("LAST_OWNER_REQUIRED", "Organization must have at least one owner");
    }
}
