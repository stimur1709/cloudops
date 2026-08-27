package com.github.stimur1709.cloudops.task.application;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionService.class);
    private static final String EXECUTION_FAILURE_MESSAGE = "Task execution failed unexpectedly";
    private static final String RETRY_EXHAUSTED_MESSAGE = "Task execution failed after retry attempts";

    private final TaskPersistenceService persistenceService;
    private final TaskHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final TaskRetryProperties retryProperties;

    public TaskExecutionService(
            TaskPersistenceService persistenceService,
            TaskHandlerRegistry handlerRegistry,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate,
            TaskRetryProperties retryProperties
    ) {
        this.persistenceService = persistenceService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
        this.retryProperties = retryProperties;
    }

    public TaskExecutionOutcome execute(long taskId) {
        TaskPersistenceService.ClaimedTask claimed;
        try {
            claimed = persistenceService.claim(taskId);
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} phase=claim", taskId, exception);
            failSafely(taskId, TaskErrorCode.EXECUTION_ERROR, EXECUTION_FAILURE_MESSAGE);
            return TaskExecutionOutcome.DEAD_LETTER;
        }

        if (claimed == null) {
            return outcomeForUnclaimed(taskId);
        }

        TaskHandler handler;
        try {
            handler = handlerRegistry.get(claimed.type());
        } catch (TaskHandlerNotFoundException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} reason=handler_not_found", taskId, exception);
            failSafely(taskId, TaskErrorCode.HANDLER_NOT_FOUND, "Task handler is not configured");
            return TaskExecutionOutcome.DEAD_LETTER;
        }

        TaskExecutionContext executionContext = new TaskExecutionContext(
                claimed.taskId(), claimed.resourceId(), claimed.type(), claimed.resourceConfig()
        );
        TaskExecutionResult result;
        try {
            result = retryTemplate.execute(
                    retryContext -> executeAttempt(handler, executionContext, retryContext.getRetryCount())
            );
        } catch (RetryableTaskExecutionException exception) {
            LOGGER.error("event=task_retry_exhausted taskId={} taskType={} attempts={}",
                    taskId, claimed.type(), effectiveMaxAttempts(), exception);
            failSafely(taskId, TaskErrorCode.RETRY_EXHAUSTED, RETRY_EXHAUSTED_MESSAGE);
            return TaskExecutionOutcome.DEAD_LETTER;
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} taskType={}",
                    taskId, claimed.type(), exception);
            failSafely(taskId, TaskErrorCode.EXECUTION_ERROR, EXECUTION_FAILURE_MESSAGE);
            return TaskExecutionOutcome.DEAD_LETTER;
        }

        try {
            save(taskId, result);
            return TaskExecutionOutcome.ACKNOWLEDGE;
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_terminal_persistence_failure taskId={} taskType={}",
                    taskId, claimed.type(), exception);
            return TaskExecutionOutcome.DEAD_LETTER;
        }
    }

    private TaskExecutionResult executeAttempt(
            TaskHandler handler,
            TaskExecutionContext executionContext,
            int priorFailureCount
    ) {
        int attempt = persistenceService.recordAttempt(executionContext.taskId());
        LOGGER.info("event=task_attempt_started taskId={} taskType={} attempt={}",
                executionContext.taskId(), executionContext.type(), attempt);
        try {
            return handler.execute(executionContext);
        } catch (RetryableTaskExecutionException exception) {
            int failureNumber = priorFailureCount + 1;
            if (failureNumber < effectiveMaxAttempts()) {
                LOGGER.warn("event=task_retry_scheduled taskId={} attempt={} nextInterval={} exceptionType={}",
                        executionContext.taskId(), attempt,
                        retryProperties.intervalAfterFailure(failureNumber), exception.getClass().getSimpleName());
            }
            throw exception;
        }
    }

    private TaskExecutionOutcome outcomeForUnclaimed(long taskId) {
        TaskStatus status = persistenceService.status(taskId);
        if (status == null) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} reason=task_not_found", taskId);
            return TaskExecutionOutcome.DEAD_LETTER;
        }
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            LOGGER.info("event=task_duplicate_terminal_skipped taskId={} status={}", taskId, status);
        } else {
            LOGGER.info("event=task_duplicate_in_progress_skipped taskId={} status={}", taskId, status);
        }
        return TaskExecutionOutcome.ACKNOWLEDGE;
    }

    private int effectiveMaxAttempts() {
        return retryProperties.enabled() ? retryProperties.maxAttempts() : 1;
    }

    private void failSafely(long taskId, TaskErrorCode errorCode, String message) {
        try {
            persistenceService.fail(taskId, errorCode, message);
        } catch (RuntimeException persistenceException) {
            LOGGER.error("event=task_failure_persistence_error taskId={} errorCode={}",
                    taskId, errorCode, persistenceException);
        }
    }

    private void save(long taskId, TaskExecutionResult result) {
        if (result instanceof TaskExecutionResult.Completed(Object completed)) {
            JsonNode json = objectMapper.valueToTree(completed);
            persistenceService.complete(taskId, json);
        } else if (result instanceof TaskExecutionResult.Failed(TaskErrorCode errorCode, String errorMessage)) {
            persistenceService.fail(taskId, errorCode, errorMessage);
        }
    }
}
