package com.github.stimur1709.cloudops.resource.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScope;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipEntity_;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity_;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class ResourceHealthScopes {

    private ResourceHealthScopes() {
    }

    public static JpaSearchScope<ResourceHealthEntity> visibleTo(long userId) {
        return (root, query, builder) -> {
            Subquery<Long> memberships = query.subquery(Long.class);
            Root<OrganizationMembershipEntity> membership = memberships.from(OrganizationMembershipEntity.class);
            memberships.select(membership.get(OrganizationMembershipEntity_.organizationId));
            memberships.where(builder.equal(
                    membership.get(OrganizationMembershipEntity_.userId), userId
            ));
            return root.get(ResourceHealthEntity_.resource)
                    .get(ResourceEntity_.organizationId)
                    .in(memberships);
        };
    }
}
