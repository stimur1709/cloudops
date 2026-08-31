package com.github.stimur1709.cloudops.common.persistence.search;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record JpaSearchDefinition<E>(
        Class<E> entityType, Map<String, JpaSearchField<E, ?>> fields, String defaultSortField) {

    public JpaSearchDefinition {
        entityType = Objects.requireNonNull(entityType);
        fields = Map.copyOf(fields);
        defaultSortField = Objects.requireNonNull(defaultSortField);

        JpaSearchField<E, ?> defaultField = fields.get(defaultSortField);
        if (defaultField == null || !defaultField.isSortable()) {
            throw new IllegalArgumentException("Default sort field must be configured and sortable");
        }
    }

    public static <E> Builder<E> builder(Class<E> entityType) {
        return new Builder<>(entityType);
    }

    public static final class Builder<E> {

        private final Class<E> entityType;
        private final Map<String, JpaSearchField<E, ?>> fields = new LinkedHashMap<>();
        private String defaultSortField;

        private Builder(Class<E> entityType) {
            this.entityType = Objects.requireNonNull(entityType);
        }

        public Builder<E> field(String name, JpaSearchField<E, ?> field) {
            if (fields.putIfAbsent(Objects.requireNonNull(name), Objects.requireNonNull(field)) != null) {
                throw new IllegalArgumentException("Search field is already configured: " + name);
            }
            return this;
        }

        public Builder<E> defaultSort(String field) {
            this.defaultSortField = Objects.requireNonNull(field);
            return this;
        }

        public JpaSearchDefinition<E> build() {
            return new JpaSearchDefinition<>(entityType, fields, defaultSortField);
        }
    }
}
