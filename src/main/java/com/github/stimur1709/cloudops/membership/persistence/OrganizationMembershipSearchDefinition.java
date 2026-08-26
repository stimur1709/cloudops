package com.github.stimur1709.cloudops.membership.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.membership.MembershipRole;

public final class OrganizationMembershipSearchDefinition {

    public static final JpaSearchDefinition<OrganizationMembershipEntity> DEFINITION =
            JpaSearchDefinition.builder(OrganizationMembershipEntity.class)
                    .field(OrganizationMembershipEntity_.ID, JpaSearchField
                            .<OrganizationMembershipEntity, Long>comparable(
                                    root -> root.get(OrganizationMembershipEntity_.id),
                                    SearchValueConverter.longInteger()
                            ).sortable())
                    .field(OrganizationMembershipEntity_.ORGANIZATION_ID, JpaSearchField
                            .<OrganizationMembershipEntity, Long>equality(
                                    root -> root.get(OrganizationMembershipEntity_.organizationId),
                                    SearchValueConverter.longInteger()
                            ))
                    .field(OrganizationMembershipEntity_.USER_ID, JpaSearchField
                            .<OrganizationMembershipEntity, Long>comparable(
                                    root -> root.get(OrganizationMembershipEntity_.userId),
                                    SearchValueConverter.longInteger()
                            ).sortable())
                    .field(OrganizationMembershipEntity_.ROLE, JpaSearchField
                            .<OrganizationMembershipEntity, MembershipRole>equality(
                                    root -> root.get(OrganizationMembershipEntity_.role),
                                    SearchValueConverter.enumeration(MembershipRole.class)
                            ).sortable())
                    .field(OrganizationMembershipEntity_.CREATED_AT, JpaSearchField
                            .<OrganizationMembershipEntity, Instant>comparable(
                                    root -> root.get(OrganizationMembershipEntity_.createdAt),
                                    SearchValueConverter.instant()
                            ).sortable())
                    .field(OrganizationMembershipEntity_.UPDATED_AT, JpaSearchField
                            .<OrganizationMembershipEntity, Instant>comparable(
                                    root -> root.get(OrganizationMembershipEntity_.updatedAt),
                                    SearchValueConverter.instant()
                            ).sortable())
                    .defaultSort(OrganizationMembershipEntity_.ID)
                    .build();

    private OrganizationMembershipSearchDefinition() {
    }
}
