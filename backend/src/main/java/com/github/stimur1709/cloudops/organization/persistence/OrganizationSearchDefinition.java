package com.github.stimur1709.cloudops.organization.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import java.time.Instant;

public final class OrganizationSearchDefinition {

    public static final JpaSearchDefinition<OrganizationEntity> DEFINITION = JpaSearchDefinition.builder(
                    OrganizationEntity.class)
            .field(
                    OrganizationEntity_.ID,
                    JpaSearchField.<OrganizationEntity, Long>comparable(
                                    root -> root.get(OrganizationEntity_.id), SearchValueConverter.longInteger())
                            .sortable())
            .field(
                    OrganizationEntity_.NAME,
                    JpaSearchField.<OrganizationEntity>text(root -> root.get(OrganizationEntity_.name))
                            .sortable())
            .field(
                    OrganizationEntity_.CREATED_AT,
                    JpaSearchField.<OrganizationEntity, Instant>comparable(
                                    root -> root.get(OrganizationEntity_.createdAt), SearchValueConverter.instant())
                            .sortable())
            .field(
                    OrganizationEntity_.UPDATED_AT,
                    JpaSearchField.<OrganizationEntity, Instant>comparable(
                                    root -> root.get(OrganizationEntity_.updatedAt), SearchValueConverter.instant())
                            .sortable())
            .defaultSort(OrganizationEntity_.ID)
            .build();

    private OrganizationSearchDefinition() {}
}
