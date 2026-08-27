package com.github.stimur1709.cloudops.task.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.stimur1709.cloudops.task.execution.TaskExecutionOutcome;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionService;
import org.junit.jupiter.api.Test;

class TaskExecutionListenerTest {

    @Test
    void delegatesTaskIdToExecutionService() {
        TaskExecutionService executionService = mock(TaskExecutionService.class);
        TaskExecutionListener listener = new TaskExecutionListener(executionService);

        listener.receive(new TaskExecutionCommand(42));

        verify(executionService).execute(42);
    }

    @Test
    void rejectsMessageWithoutRequeueForDeadLetterOutcome() {
        TaskExecutionService executionService = mock(TaskExecutionService.class);
        when(executionService.execute(42)).thenReturn(TaskExecutionOutcome.DEAD_LETTER);
        TaskExecutionListener listener = new TaskExecutionListener(executionService);

        assertThatThrownBy(() -> listener.receive(new TaskExecutionCommand(42)))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    }
}
