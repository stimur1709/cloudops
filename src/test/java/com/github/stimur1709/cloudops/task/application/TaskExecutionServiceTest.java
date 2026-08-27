package com.github.stimur1709.cloudops.task.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TaskExecutionServiceTest {

    @Test
    void turnsMissingHandlerIntoControlledFailure() {
        TaskPersistenceService persistence = mock(TaskPersistenceService.class);
        TaskHandlerRegistry registry = mock(TaskHandlerRegistry.class);
        when(persistence.claim(7)).thenReturn(new TaskPersistenceService.ClaimedTask(
                7, 8, TaskType.HTTP_CHECK, new OtherResourceConfig()
        ));
        when(registry.get(TaskType.HTTP_CHECK)).thenThrow(new TaskHandlerNotFoundException(TaskType.HTTP_CHECK));
        TaskExecutionService service = new TaskExecutionService(persistence, registry, new ObjectMapper());

        service.execute(7);

        verify(persistence).fail(7, TaskErrorCode.HANDLER_NOT_FOUND, "Task handler is not configured");
    }

    @Test
    void ignoresUnknownOrAlreadyClaimedTask() {
        TaskPersistenceService persistence = mock(TaskPersistenceService.class);
        TaskHandlerRegistry registry = mock(TaskHandlerRegistry.class);
        TaskExecutionService service = new TaskExecutionService(persistence, registry, new ObjectMapper());

        service.execute(404);

        verify(persistence).claim(404);
    }
}
