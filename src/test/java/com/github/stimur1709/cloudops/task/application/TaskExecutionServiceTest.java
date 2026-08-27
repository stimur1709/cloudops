package com.github.stimur1709.cloudops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

class TaskExecutionServiceTest {

    private TaskPersistenceService persistence;
    private TaskHandlerRegistry registry;
    private TaskHandler handler;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        persistence = mock(TaskPersistenceService.class);
        registry = mock(TaskHandlerRegistry.class);
        handler = mock(TaskHandler.class);
        TaskRetryProperties properties = new TaskRetryProperties(
                true, 3, Duration.ofMillis(1), 2.0, Duration.ofMillis(2)
        );
        RetryTemplate retryTemplate = new TaskRetryConfiguration().taskRetryTemplate(properties);
        service = new TaskExecutionService(persistence, registry, new ObjectMapper(), retryTemplate, properties);
    }

    @Test
    void invokesSuccessfulHandlerOnce() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(TaskExecutionResult.completed("done"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler).execute(org.mockito.ArgumentMatchers.any());
        verify(persistence).recordAttempt(7);
        verify(persistence).complete(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retriesExplicitTemporaryFailureThenCompletes() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RetryableTaskExecutionException("temporary"))
                .thenReturn(TaskExecutionResult.completed("done"));
        when(persistence.recordAttempt(7)).thenReturn(1, 2);

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler, times(2)).execute(org.mockito.ArgumentMatchers.any());
        verify(persistence, times(2)).recordAttempt(7);
    }

    @Test
    void exhaustsRetryAndReturnsDeadLetterOutcome() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RetryableTaskExecutionException("temporary"));
        when(persistence.recordAttempt(7)).thenReturn(1, 2, 3);

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler, times(3)).execute(org.mockito.ArgumentMatchers.any());
        verify(persistence).fail(7, TaskErrorCode.RETRY_EXHAUSTED, "Task execution failed after retry attempts");
    }

    @Test
    void doesNotRetryUnclassifiedRuntimeException() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("invalid"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler).execute(org.mockito.ArgumentMatchers.any());
        verify(persistence).fail(7, TaskErrorCode.EXECUTION_ERROR, "Task execution failed unexpectedly");
    }

    @Test
    void controlledNegativeResultIsAcknowledgedWithoutRetry() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(TaskExecutionResult.failed(TaskErrorCode.TIMEOUT, "HTTP check timed out"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler).execute(org.mockito.ArgumentMatchers.any());
        verify(persistence).fail(7, TaskErrorCode.TIMEOUT, "HTTP check timed out");
    }

    @Test
    void turnsMissingHandlerIntoControlledDeadLetterFailure() {
        arrangeClaim();
        when(registry.get(TaskType.HTTP_CHECK)).thenThrow(new TaskHandlerNotFoundException(TaskType.HTTP_CHECK));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(persistence).fail(7, TaskErrorCode.HANDLER_NOT_FOUND, "Task handler is not configured");
        verify(persistence, never()).recordAttempt(7);
    }

    @Test
    void deadLettersUnknownTaskButAcknowledgesTerminalDuplicate() {
        when(persistence.status(404)).thenReturn(null);
        assertThat(service.execute(404)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        when(persistence.status(8)).thenReturn(TaskStatus.COMPLETED);
        assertThat(service.execute(8)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(registry, never()).get(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotRerunHandlerWhenTerminalResultCannotBeSaved() {
        arrangeClaimedTask();
        when(handler.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(TaskExecutionResult.completed("done"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(persistence).complete(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler).execute(org.mockito.ArgumentMatchers.any());
    }

    private void arrangeClaimedTask() {
        arrangeClaim();
        when(registry.get(TaskType.HTTP_CHECK)).thenReturn(handler);
        when(persistence.recordAttempt(7)).thenReturn(1);
    }

    private void arrangeClaim() {
        when(persistence.claim(7)).thenReturn(new TaskPersistenceService.ClaimedTask(
                7, 8, TaskType.HTTP_CHECK, new OtherResourceConfig()
        ));
    }
}
