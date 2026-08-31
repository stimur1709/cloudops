package com.github.stimur1709.cloudops.membership.api;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddOrganizationMemberRequest(
        @NotNull(message = "User id is required") @Positive(message = "User id must be greater than 0") Long userId,

        @NotNull(message = "Role is required") MembershipRole role) {}
