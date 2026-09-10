package com.github.stimur1709.cloudops.membership.api;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import java.time.Instant;

public record OrganizationMemberResponse(
        long id, long organizationId, long userId, MembershipRole role, Instant createdAt, Instant updatedAt) {
    public static OrganizationMemberResponse from(OrganizationMembershipEntity membership) {
        return new OrganizationMemberResponse(
                membership.id(),
                membership.organizationId(),
                membership.userId(),
                membership.role(),
                membership.createdAt(),
                membership.updatedAt());
    }
}
