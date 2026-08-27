package com.github.stimur1709.cloudops.task.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.github.stimur1709.cloudops.task.application.TaskExecutionService;
import org.junit.jupiter.api.Test;

class TaskExecutionListenerTest {

    @Test
    void delegatesTaskIdToExecutionService() {
        TaskExecutionService executionService = mock(TaskExecutionService.class);
        TaskExecutionListener listener = new TaskExecutionListener(executionService);

        listener.receive(new TaskExecutionCommand(42));

        verify(executionService).execute(42);
    }
}
