package com.github.stimur1709.cloudops.task.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.application.StaleTaskExecutionException;
import com.github.stimur1709.cloudops.task.application.TaskPersistenceService;
import com.github.stimur1709.cloudops.task.config.TaskRetryConfiguration;
import com.github.stimur1709.cloudops.task.config.TaskRetryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

class TaskExecutionServiceTest {

    private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private TaskPersistenceService persistence;
    private TaskHandlerRegistry registry;
    private TaskHandler handler;
    private TaskLeaseManager leaseManager;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        persistence = mock(TaskPersistenceService.class);
        registry = mock(TaskHandlerRegistry.class);
        handler = mock(TaskHandler.class);
        leaseManager = mock(TaskLeaseManager.class);
        TaskRetryProperties properties = new TaskRetryProperties(
                true, 3, Duration.ofMillis(1), 2.0, Duration.ofMillis(2)
        );
        RetryTemplate retryTemplate = new TaskRetryConfiguration().taskRetryTemplate(properties);
        service = new TaskExecutionService(
                persistence, registry, new ObjectMapper(), retryTemplate, properties, leaseManager
        );
    }

    @Test
    void invokesSuccessfulHandlerOnce() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenReturn(TaskExecutionResult.completed("done"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler).execute(any());
        verify(persistence).recordAttempt(7, EXECUTION_ID);
        verify(persistence).complete(eq(7L), eq(EXECUTION_ID), any());
        verify(leaseManager).register(7, EXECUTION_ID);
        verify(leaseManager).unregister(7, EXECUTION_ID);
    }

    @Test
    void retriesExplicitTemporaryFailureThenCompletes() {
        arrangeClaimedTask();
        when(handler.execute(any()))
                .thenThrow(new RetryableTaskExecutionException("temporary"))
                .thenReturn(TaskExecutionResult.completed("done"));
        when(persistence.recordAttempt(7, EXECUTION_ID)).thenReturn(1, 2);

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler, times(2)).execute(any());
        verify(persistence, times(2)).recordAttempt(7, EXECUTION_ID);
    }

    @Test
    void exhaustsRetryAndReturnsDeadLetterOutcome() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenThrow(new RetryableTaskExecutionException("temporary"));
        when(persistence.recordAttempt(7, EXECUTION_ID)).thenReturn(1, 2, 3);

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler, times(3)).execute(any());
        verify(persistence).fail(7, EXECUTION_ID, TaskErrorCode.RETRY_EXHAUSTED,
                "Task execution failed after retry attempts");
    }

    @Test
    void doesNotRetryUnclassifiedRuntimeException() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenThrow(new IllegalStateException("invalid"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler).execute(any());
        verify(persistence).fail(7, EXECUTION_ID, TaskErrorCode.EXECUTION_ERROR,
                "Task execution failed unexpectedly");
    }

    @Test
    void controlledNegativeResultIsAcknowledgedWithoutRetry() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenReturn(TaskExecutionResult.failed(TaskErrorCode.EXECUTION_ERROR, "Operation failed"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(persistence).fail(7, EXECUTION_ID, TaskErrorCode.EXECUTION_ERROR, "Operation failed");
    }

    @Test
    void completedOperationCompletesTask() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenReturn(TaskExecutionResult.completed("done"));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(persistence).complete(eq(7L), eq(EXECUTION_ID), any());
        verify(persistence, never()).fail(eq(7L), eq(EXECUTION_ID), any(), any());
    }

    @Test
    void turnsMissingHandlerIntoControlledDeadLetterFailure() {
        arrangeClaim();
        when(registry.get(com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE))
                .thenThrow(new TaskHandlerNotFoundException(com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(persistence).fail(7, EXECUTION_ID, TaskErrorCode.HANDLER_NOT_FOUND,
                "Task handler is not configured");
        verify(persistence, never()).recordAttempt(anyLong(), any());
    }

    @Test
    void acknowledgesExecutionFencedBeforeAttempt() {
        arrangeClaimedTask();
        when(persistence.recordAttempt(7, EXECUTION_ID))
                .thenThrow(new StaleTaskExecutionException(7, EXECUTION_ID));

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(handler, never()).execute(any());
    }

    @Test
    void acknowledgesExceptionFromHandlerWhenFailureUpdateIsFenced() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenThrow(new IllegalStateException("late failure"));
        when(persistence.fail(7, EXECUTION_ID, TaskErrorCode.EXECUTION_ERROR,
                "Task execution failed unexpectedly")).thenReturn(false);

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);
    }

    @Test
    void deadLettersUnknownTaskButAcknowledgesTerminalDuplicate() {
        when(persistence.status(404)).thenReturn(null);
        assertThat(service.execute(404)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        when(persistence.status(8)).thenReturn(TaskStatus.COMPLETED);
        assertThat(service.execute(8)).isEqualTo(TaskExecutionOutcome.ACKNOWLEDGE);

        verify(registry, never()).get(any());
    }

    @Test
    void doesNotRerunHandlerWhenTerminalResultCannotBeSaved() {
        arrangeClaimedTask();
        when(handler.execute(any())).thenReturn(TaskExecutionResult.completed("done"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(persistence).complete(eq(7L), eq(EXECUTION_ID), any());

        assertThat(service.execute(7)).isEqualTo(TaskExecutionOutcome.DEAD_LETTER);

        verify(handler).execute(any());
    }

    private void arrangeClaimedTask() {
        arrangeClaim();
        when(registry.get(com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE)).thenReturn(handler);
        when(persistence.recordAttempt(7, EXECUTION_ID)).thenReturn(1);
    }

    private void arrangeClaim() {
        when(persistence.claim(7)).thenReturn(new TaskPersistenceService.ClaimedTask(
                7, 8, com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE, new OtherResourceConfig(), EXECUTION_ID
        ));
        when(persistence.fail(eq(7L), eq(EXECUTION_ID), any(), any())).thenReturn(true);
    }
}
