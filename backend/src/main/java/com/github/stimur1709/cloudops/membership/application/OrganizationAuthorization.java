package com.github.stimur1709.cloudops.membership.application;

import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAuthorization {

    private static final EnumSet<MembershipRole> MANAGERS = EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN);

    private final OrganizationMembershipJpaRepository membershipRepository;

    public OrganizationAuthorization(OrganizationMembershipJpaRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public MembershipRole requireMember(long organizationId, long userId) {
        return membershipRepository.findRole(organizationId, userId).orElseThrow(NotFoundException::new);
    }

    public void requireManager(long organizationId, long userId) {
        MembershipRole role = requireMember(organizationId, userId);
        if (!MANAGERS.contains(role)) {
            throw new ForbiddenException();
        }
    }

    public void requireOwner(long organizationId, long userId) {
        MembershipRole role = requireMember(organizationId, userId);
        if (role != MembershipRole.OWNER) {
            throw new ForbiddenException();
        }
    }
}
