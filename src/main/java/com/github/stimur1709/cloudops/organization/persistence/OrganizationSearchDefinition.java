package com.github.stimur1709.cloudops.organization.persistence;

import java.time.Instant;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;

public final class OrganizationSearchDefinition {

    public static final JpaSearchDefinition<OrganizationEntity> DEFINITION =
            JpaSearchDefinition.builder(OrganizationEntity.class)
                    .field("id", JpaSearchField.<OrganizationEntity, Long>comparable(
                            root -> root.get("id"), SearchValueConverter.longInteger()
                    ).sortable())
                    .field("name", JpaSearchField.<OrganizationEntity>text(
                            root -> root.get("name")
                    ).sortable())
                    .field("createdAt", JpaSearchField.<OrganizationEntity, Instant>comparable(
                            root -> root.get("createdAt"), SearchValueConverter.instant()
                    ).sortable())
                    .field("updatedAt", JpaSearchField.<OrganizationEntity, Instant>comparable(
                            root -> root.get("updatedAt"), SearchValueConverter.instant()
                    ).sortable())
                    .defaultSort("id")
                    .build();

    private OrganizationSearchDefinition() {
    }
}
