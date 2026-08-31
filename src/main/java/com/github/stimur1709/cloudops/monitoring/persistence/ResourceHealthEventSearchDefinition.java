package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import jakarta.persistence.metamodel.SingularAttribute;
import java.time.Instant;

public final class ResourceHealthEventSearchDefinition {

    public static final JpaSearchDefinition<ResourceHealthEventEntity> DEFINITION = JpaSearchDefinition.builder(
                    ResourceHealthEventEntity.class)
            .field(ResourceHealthEventEntity_.ID, comparableLong(ResourceHealthEventEntity_.id))
            .field(ResourceHealthEventEntity_.FROM_STATUS, status(ResourceHealthEventEntity_.fromStatus))
            .field(ResourceHealthEventEntity_.TO_STATUS, status(ResourceHealthEventEntity_.toStatus))
            .field(ResourceHealthEventEntity_.CHANGED_AT, comparableInstant(ResourceHealthEventEntity_.changedAt))
            .defaultSort(ResourceHealthEventEntity_.CHANGED_AT)
            .build();

    private ResourceHealthEventSearchDefinition() {}

    private static JpaSearchField<ResourceHealthEventEntity, Long> comparableLong(
            SingularAttribute<ResourceHealthEventEntity, Long> attribute) {
        return JpaSearchField.<ResourceHealthEventEntity, Long>comparable(
                        root -> root.get(attribute), SearchValueConverter.longInteger())
                .sortable();
    }

    private static JpaSearchField<ResourceHealthEventEntity, ResourceHealthStatus> status(
            SingularAttribute<ResourceHealthEventEntity, ResourceHealthStatus> attribute) {
        return JpaSearchField.<ResourceHealthEventEntity, ResourceHealthStatus>equality(
                        root -> root.get(attribute), SearchValueConverter.enumeration(ResourceHealthStatus.class))
                .sortable();
    }

    private static JpaSearchField<ResourceHealthEventEntity, Instant> comparableInstant(
            SingularAttribute<ResourceHealthEventEntity, Instant> attribute) {
        return JpaSearchField.<ResourceHealthEventEntity, Instant>comparable(
                        root -> root.get(attribute), SearchValueConverter.instant())
                .sortable();
    }
}
