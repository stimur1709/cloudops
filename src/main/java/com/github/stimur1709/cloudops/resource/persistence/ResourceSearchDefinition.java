package com.github.stimur1709.cloudops.resource.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEntity_;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import java.time.Instant;

public final class ResourceSearchDefinition {

    public static final JpaSearchDefinition<ResourceHealthEntity> DEFINITION = JpaSearchDefinition.builder(
                    ResourceHealthEntity.class)
            .field(
                    ResourceEntity_.ID,
                    JpaSearchField.<ResourceHealthEntity, Long>comparable(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.id),
                                    SearchValueConverter.longInteger())
                            .sortable())
            .field(
                    ResourceEntity_.NAME,
                    JpaSearchField.<ResourceHealthEntity>text(root ->
                                    root.get(ResourceHealthEntity_.resource).get(ResourceEntity_.name))
                            .sortable())
            .field(
                    ResourceEntity_.TYPE,
                    JpaSearchField.<ResourceHealthEntity, ResourceType>equality(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.type),
                                    SearchValueConverter.enumeration(ResourceType.class))
                            .sortable())
            .field(
                    ResourceEntity_.STATUS,
                    JpaSearchField.<ResourceHealthEntity, ResourceStatus>equality(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.status),
                                    SearchValueConverter.enumeration(ResourceStatus.class))
                            .sortable())
            .field(
                    ResourceEntity_.ORGANIZATION_ID,
                    JpaSearchField.<ResourceHealthEntity, Long>comparable(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.organizationId),
                                    SearchValueConverter.longInteger())
                            .sortable())
            .field(
                    ResourceEntity_.CREATED_AT,
                    JpaSearchField.<ResourceHealthEntity, Instant>comparable(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.createdAt),
                                    SearchValueConverter.instant())
                            .sortable())
            .field(
                    ResourceEntity_.UPDATED_AT,
                    JpaSearchField.<ResourceHealthEntity, Instant>comparable(
                                    root -> root.get(ResourceHealthEntity_.resource)
                                            .get(ResourceEntity_.updatedAt),
                                    SearchValueConverter.instant())
                            .sortable())
            .field(
                    "healthStatus",
                    JpaSearchField.<ResourceHealthEntity, ResourceHealthStatus>equality(
                            root -> root.get(ResourceHealthEntity_.healthStatus),
                            SearchValueConverter.enumeration(ResourceHealthStatus.class)))
            .defaultSort(ResourceEntity_.ID)
            .build();

    private ResourceSearchDefinition() {}
}
