package com.github.stimur1709.cloudops.common.persistence.search;

import java.util.Map;
import java.util.Objects;

public record JpaSearchDefinition<E>(
        Class<E> entityType,
        Map<String, JpaSearchField<E, ?>> fields,
        String defaultSortField
) {

    public JpaSearchDefinition {
        entityType = Objects.requireNonNull(entityType);
        fields = Map.copyOf(fields);
        defaultSortField = Objects.requireNonNull(defaultSortField);

        JpaSearchField<E, ?> defaultField = fields.get(defaultSortField);
        if (defaultField == null || !defaultField.isSortable()) {
            throw new IllegalArgumentException("Default sort field must be configured and sortable");
        }
    }
}
