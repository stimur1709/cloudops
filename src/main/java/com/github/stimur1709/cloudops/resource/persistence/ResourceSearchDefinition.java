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
                    .field(ResourceEntity_.ID, JpaSearchField.<ResourceEntity, Long>comparable(
                            root -> root.get(ResourceEntity_.id), SearchValueConverter.longInteger()
                    ).sortable())
                    .field(ResourceEntity_.NAME, JpaSearchField.<ResourceEntity>text(
                            root -> root.get(ResourceEntity_.name)
                    ).sortable())
                    .field(ResourceEntity_.TYPE, JpaSearchField.<ResourceEntity, ResourceType>equality(
                            root -> root.get(ResourceEntity_.type),
                            SearchValueConverter.enumeration(ResourceType.class)
                    ).sortable())
                    .field(ResourceEntity_.STATUS, JpaSearchField.<ResourceEntity, ResourceStatus>equality(
                            root -> root.get(ResourceEntity_.status),
                            SearchValueConverter.enumeration(ResourceStatus.class)
                    ).sortable())
                    .field(ResourceEntity_.ORGANIZATION_ID, JpaSearchField.<ResourceEntity, Long>comparable(
                            root -> root.get(ResourceEntity_.organizationId), SearchValueConverter.longInteger()
                    ).sortable())
                    .field(ResourceEntity_.CREATED_AT, JpaSearchField.<ResourceEntity, Instant>comparable(
                            root -> root.get(ResourceEntity_.createdAt), SearchValueConverter.instant()
                    ).sortable())
                    .field(ResourceEntity_.UPDATED_AT, JpaSearchField.<ResourceEntity, Instant>comparable(
                            root -> root.get(ResourceEntity_.updatedAt), SearchValueConverter.instant()
                    ).sortable())
                    .defaultSort(ResourceEntity_.ID)
                    .build();

    private ResourceSearchDefinition() {
    }
}
