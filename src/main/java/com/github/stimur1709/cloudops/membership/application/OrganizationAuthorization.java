package com.github.stimur1709.cloudops.membership.application;

import java.util.EnumSet;

import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAuthorization {

    private static final EnumSet<MembershipRole> MANAGERS =
            EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN);

    private final OrganizationMembershipJpaRepository membershipRepository;

    public OrganizationAuthorization(OrganizationMembershipJpaRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public MembershipRole requireMember(long organizationId, long userId, String hiddenEntityName) {
        return membershipRepository.findRole(organizationId, userId)
                .orElseThrow(() -> new NotFoundException(hiddenEntityName));
    }

    public MembershipRole requireManager(long organizationId, long userId, String hiddenEntityName) {
        MembershipRole role = requireMember(organizationId, userId, hiddenEntityName);
        if (!MANAGERS.contains(role)) {
            throw new ForbiddenException();
        }
        return role;
    }

    public void requireOwner(long organizationId, long userId, String hiddenEntityName) {
        MembershipRole role = requireMember(organizationId, userId, hiddenEntityName);
        if (role != MembershipRole.OWNER) {
            throw new ForbiddenException();
        }
    }
}
