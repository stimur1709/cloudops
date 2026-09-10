package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import java.time.Instant;

public final class MonitoringResultSearchDefinition {

    public static final JpaSearchDefinition<MonitoringResultEntity> DEFINITION = JpaSearchDefinition.builder(
                    MonitoringResultEntity.class)
            .field(MonitoringResultEntity_.ID, comparableLong(MonitoringResultEntity_.id))
            .field(MonitoringResultEntity_.CHECKED_AT, comparableInstant(MonitoringResultEntity_.checkedAt))
            .defaultSort(MonitoringResultEntity_.CHECKED_AT)
            .build();

    private MonitoringResultSearchDefinition() {}

    private static JpaSearchField<MonitoringResultEntity, Long> comparableLong(
            jakarta.persistence.metamodel.SingularAttribute<MonitoringResultEntity, Long> attribute) {
        return JpaSearchField.<MonitoringResultEntity, Long>comparable(
                        root -> root.get(attribute), SearchValueConverter.longInteger())
                .sortable();
    }

    private static JpaSearchField<MonitoringResultEntity, Instant> comparableInstant(
            jakarta.persistence.metamodel.SingularAttribute<MonitoringResultEntity, Instant> attribute) {
        return JpaSearchField.<MonitoringResultEntity, Instant>comparable(
                        root -> root.get(attribute), SearchValueConverter.instant())
                .sortable();
    }
}
