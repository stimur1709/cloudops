package com.github.stimur1709.cloudops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.github.stimur1709.cloudops.task.TaskType;
import org.junit.jupiter.api.Test;

class TaskHandlerRegistryTest {

    @Test
    void resolvesHandlerByTaskType() {
        TaskHandler handler = handler(TaskType.HTTP_CHECK);

        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(handler));

        assertThat(registry.get(TaskType.HTTP_CHECK)).isSameAs(handler);
    }

    @Test
    void rejectsDuplicateHandlersForSameTaskType() {
        TaskHandler first = handler(TaskType.HTTP_CHECK);
        TaskHandler second = handler(TaskType.HTTP_CHECK);

        assertThatIllegalStateException()
                .isThrownBy(() -> new TaskHandlerRegistry(List.of(first, second)))
                .withMessageContaining("HTTP_CHECK");
    }

    private TaskHandler handler(TaskType type) {
        TaskHandler handler = mock(TaskHandler.class);
        when(handler.supports()).thenReturn(type);
        return handler;
    }
}
