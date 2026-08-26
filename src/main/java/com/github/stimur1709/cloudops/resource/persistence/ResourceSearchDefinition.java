package com.github.stimur1709.cloudops.resource.persistence;

import java.time.Instant;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;

public final class ResourceSearchDefinition {

    public static final JpaSearchDefinition<ResourceEntity> DEFINITION =
            JpaSearchDefinition.builder(ResourceEntity.class)
                    .field("id", JpaSearchField.<ResourceEntity, Long>comparable(
                            root -> root.get("id"), SearchValueConverter.longInteger()
                    ).sortable())
                    .field("name", JpaSearchField.<ResourceEntity>text(
                            root -> root.get("name")
                    ).sortable())
                    .field("type", JpaSearchField.<ResourceEntity, ResourceType>equality(
                            root -> root.get("type"), SearchValueConverter.enumeration(ResourceType.class)
                    ).sortable())
                    .field("status", JpaSearchField.<ResourceEntity, ResourceStatus>equality(
                            root -> root.get("status"), SearchValueConverter.enumeration(ResourceStatus.class)
                    ).sortable())
                    .field("organizationId", JpaSearchField.<ResourceEntity, Long>comparable(
                            root -> root.get("organizationId"), SearchValueConverter.longInteger()
                    ).sortable())
                    .field("createdAt", JpaSearchField.<ResourceEntity, Instant>comparable(
                            root -> root.get("createdAt"), SearchValueConverter.instant()
                    ).sortable())
                    .field("updatedAt", JpaSearchField.<ResourceEntity, Instant>comparable(
                            root -> root.get("updatedAt"), SearchValueConverter.instant()
                    ).sortable())
                    .defaultSort("id")
                    .build();

    private ResourceSearchDefinition() {
    }
}
