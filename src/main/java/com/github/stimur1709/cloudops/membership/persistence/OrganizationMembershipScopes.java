package com.github.stimur1709.cloudops.membership.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchScope;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.SingularAttribute;

public final class OrganizationMembershipScopes {

    private OrganizationMembershipScopes() {
    }

    public static <E> JpaSearchScope<E> visibleTo(
            long userId,
            SingularAttribute<? super E, Long> organizationIdAttribute
    ) {
        return (root, query, builder) -> {
            Subquery<Long> memberships = query.subquery(Long.class);
            Root<OrganizationMembershipEntity> membership = memberships.from(OrganizationMembershipEntity.class);
            memberships.select(membership.get(OrganizationMembershipEntity_.organizationId));
            memberships.where(builder.equal(
                    membership.get(OrganizationMembershipEntity_.userId), userId
            ));
            return root.get(organizationIdAttribute).in(memberships);
        };
    }
}
