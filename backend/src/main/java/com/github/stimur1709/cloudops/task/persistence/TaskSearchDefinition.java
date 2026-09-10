package com.github.stimur1709.cloudops.task.persistence;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import java.time.Instant;

public final class TaskSearchDefinition {

    public static final JpaSearchDefinition<TaskEntity> DEFINITION = JpaSearchDefinition.builder(TaskEntity.class)
            .field(TaskEntity_.ID, comparableLong(TaskEntity_.id))
            .field(TaskEntity_.ORGANIZATION_ID, comparableLong(TaskEntity_.organizationId))
            .field(TaskEntity_.RESOURCE_ID, comparableLong(TaskEntity_.resourceId))
            .field(
                    TaskEntity_.TYPE,
                    JpaSearchField.<TaskEntity, TaskType>equality(
                                    root -> root.get(TaskEntity_.type),
                                    SearchValueConverter.enumeration(TaskType.class))
                            .sortable())
            .field(
                    TaskEntity_.STATUS,
                    JpaSearchField.<TaskEntity, TaskStatus>equality(
                                    root -> root.get(TaskEntity_.status),
                                    SearchValueConverter.enumeration(TaskStatus.class))
                            .sortable())
            .field(TaskEntity_.CREATED_BY, comparableLong(TaskEntity_.createdBy))
            .field(TaskEntity_.CREATED_AT, comparableInstant(TaskEntity_.createdAt))
            .field(TaskEntity_.STARTED_AT, comparableInstant(TaskEntity_.startedAt))
            .field(TaskEntity_.COMPLETED_AT, comparableInstant(TaskEntity_.completedAt))
            .defaultSort(TaskEntity_.ID)
            .build();

    private TaskSearchDefinition() {}

    private static JpaSearchField<TaskEntity, Long> comparableLong(
            jakarta.persistence.metamodel.SingularAttribute<TaskEntity, Long> attribute) {
        return JpaSearchField.<TaskEntity, Long>comparable(
                        root -> root.get(attribute), SearchValueConverter.longInteger())
                .sortable();
    }

    private static JpaSearchField<TaskEntity, Instant> comparableInstant(
            jakarta.persistence.metamodel.SingularAttribute<TaskEntity, Instant> attribute) {
        return JpaSearchField.<TaskEntity, Instant>comparable(
                        root -> root.get(attribute), SearchValueConverter.instant())
                .sortable();
    }
}
