package com.github.stimur1709.cloudops.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationJpaRepository;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMembershipService {

    private final OrganizationMembershipJpaRepository membershipRepository;
    private final OrganizationJpaRepository organizationRepository;
    private final UserJpaRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public OrganizationMembershipService(
            OrganizationMembershipJpaRepository membershipRepository,
            OrganizationJpaRepository organizationRepository,
            UserJpaRepository userRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public OrganizationMembershipEntity add(long organizationId, long userId, MembershipRole role) {
        OrganizationEntity organization = getOrganizationForUpdate(organizationId);
        UserEntity user = getUser(userId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        if (memberships.stream().anyMatch(item -> item.userId() == userId)) {
            throw membershipConflict();
        }
        if (memberships.isEmpty() && role != MembershipRole.OWNER) {
            throw lastOwnerConflict();
        }
        try {
            return membershipRepository.saveAndFlush(
                    OrganizationMembershipEntity.create(organization, user, role, clock.instant())
            );
        } catch (DataIntegrityViolationException exception) {
            throw membershipConflict();
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationMembershipEntity> list(long organizationId, int start, int size) {
        getOrganization(organizationId);
        return entityManager.createQuery("""
                        select membership from OrganizationMembershipEntity membership
                        where membership.organizationId = :organizationId
                        order by membership.id
                        """, OrganizationMembershipEntity.class)
                .setParameter("organizationId", organizationId)
                .setFirstResult(start)
                .setMaxResults(size)
                .getResultList();
    }

    @Transactional
    public OrganizationMembershipEntity updateRole(
            long organizationId,
            long userId,
            MembershipRole role
    ) {
        getOrganizationForUpdate(organizationId);
        getUser(userId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        OrganizationMembershipEntity membership = find(memberships, userId);
        if (membership.role() == MembershipRole.OWNER && role != MembershipRole.OWNER
                && ownerCount(memberships) == 1) {
            throw lastOwnerConflict();
        }
        Instant now = clock.instant();
        Instant updatedAt = now.isAfter(membership.updatedAt())
                ? now
                : membership.updatedAt().plusNanos(1_000);
        membership.changeRole(role, updatedAt);
        return membership;
    }

    @Transactional
    public void remove(long organizationId, long userId) {
        getOrganizationForUpdate(organizationId);
        getUser(userId);
        List<OrganizationMembershipEntity> memberships = membershipRepository.lockAllByOrganizationId(organizationId);
        OrganizationMembershipEntity membership = find(memberships, userId);
        if (membership.role() == MembershipRole.OWNER && ownerCount(memberships) == 1) {
            throw lastOwnerConflict();
        }
        membershipRepository.delete(membership);
    }

    private OrganizationEntity getOrganization(long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
    }

    private OrganizationEntity getOrganizationForUpdate(long id) {
        return organizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Organization"));
    }

    private UserEntity getUser(long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    private OrganizationMembershipEntity find(List<OrganizationMembershipEntity> memberships, long userId) {
        return memberships.stream()
                .filter(item -> item.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Membership"));
    }

    private long ownerCount(List<OrganizationMembershipEntity> memberships) {
        return memberships.stream().filter(item -> item.role() == MembershipRole.OWNER).count();
    }

    private ConflictException membershipConflict() {
        return new ConflictException(
                "MEMBERSHIP_CONFLICT",
                "User is already a member of this organization"
        );
    }

    private ConflictException lastOwnerConflict() {
        return new ConflictException(
                "LAST_OWNER_REQUIRED",
                "Organization must have at least one owner"
        );
    }
}
