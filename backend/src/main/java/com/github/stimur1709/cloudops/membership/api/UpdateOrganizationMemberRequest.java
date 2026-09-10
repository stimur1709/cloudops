package com.github.stimur1709.cloudops.membership.api;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationMemberRequest(
        @NotNull(message = "Role is required") MembershipRole role) {}
