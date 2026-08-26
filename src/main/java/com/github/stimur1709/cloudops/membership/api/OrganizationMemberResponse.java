package com.github.stimur1709.cloudops.membership.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;

public record OrganizationMemberResponse(
        long id,
        long organizationId,
        long userId,
        MembershipRole role,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationMemberResponse from(OrganizationMembershipEntity membership) {
        return new OrganizationMemberResponse(
                membership.id(), membership.organizationId(), membership.userId(), membership.role(),
                membership.createdAt(), membership.updatedAt()
        );
    }
}
