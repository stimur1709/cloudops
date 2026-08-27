package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.application.TaskPersistenceService;
import com.github.stimur1709.cloudops.task.application.StaleTaskExecutionException;
import com.github.stimur1709.cloudops.task.config.TaskRetryProperties;
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
    private final TaskLeaseManager leaseManager;

    public TaskExecutionService(
            TaskPersistenceService persistenceService,
            TaskHandlerRegistry handlerRegistry,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate,
            TaskRetryProperties retryProperties,
            TaskLeaseManager leaseManager
    ) {
        this.persistenceService = persistenceService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
        this.retryProperties = retryProperties;
        this.leaseManager = leaseManager;
    }

    public TaskExecutionOutcome execute(long taskId) {
        TaskPersistenceService.ClaimedTask claimed;
        try {
            claimed = persistenceService.claim(taskId);
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} phase=claim", taskId, exception);
            return TaskExecutionOutcome.DEAD_LETTER;
        }

        if (claimed == null) {
            return outcomeForUnclaimed(taskId);
        }

        leaseManager.register(taskId, claimed.executionId());
        try {
            return executeClaimed(claimed);
        } finally {
            leaseManager.unregister(taskId, claimed.executionId());
        }
    }

    private TaskExecutionOutcome executeClaimed(TaskPersistenceService.ClaimedTask claimed) {
        long taskId = claimed.taskId();
        TaskHandler handler;
        try {
            handler = handlerRegistry.get(claimed.type());
        } catch (TaskHandlerNotFoundException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} reason=handler_not_found", taskId, exception);
            return outcomeAfterFailureSave(taskId, claimed.executionId(), TaskErrorCode.HANDLER_NOT_FOUND,
                    "Task handler is not configured");
        }

        TaskExecutionContext executionContext = new TaskExecutionContext(
                claimed.taskId(), claimed.resourceId(), claimed.type(), claimed.resourceConfig(), claimed.executionId()
        );
        TaskExecutionResult result;
        try {
            result = retryTemplate.execute(
                    retryContext -> executeAttempt(handler, executionContext, retryContext.getRetryCount())
            );
        } catch (RetryableTaskExecutionException exception) {
            LOGGER.error("event=task_retry_exhausted taskId={} taskType={} attempts={}",
                    taskId, claimed.type(), effectiveMaxAttempts(), exception);
            return outcomeAfterFailureSave(
                    taskId, claimed.executionId(), TaskErrorCode.RETRY_EXHAUSTED, RETRY_EXHAUSTED_MESSAGE
            );
        } catch (StaleTaskExecutionException exception) {
            LOGGER.warn("event=task_lease_lost taskId={} executionId={}", taskId, claimed.executionId());
            return TaskExecutionOutcome.ACKNOWLEDGE;
        } catch (RuntimeException exception) {
            LOGGER.error("event=task_non_retryable_rejection taskId={} taskType={}",
                    taskId, claimed.type(), exception);
            return outcomeAfterFailureSave(
                    taskId, claimed.executionId(), TaskErrorCode.EXECUTION_ERROR, EXECUTION_FAILURE_MESSAGE
            );
        }

        try {
            if (!save(taskId, claimed.executionId(), result)) {
                LOGGER.warn("event=stale_execution_result_ignored taskId={} executionId={}",
                        taskId, claimed.executionId());
            }
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
        int attempt = persistenceService.recordAttempt(executionContext.taskId(), executionContext.executionId());
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

    private TaskExecutionOutcome outcomeAfterFailureSave(
            long taskId, java.util.UUID executionId, TaskErrorCode errorCode, String message
    ) {
        try {
            if (!persistenceService.fail(taskId, executionId, errorCode, message)) {
                LOGGER.warn("event=stale_execution_result_ignored taskId={} executionId={}", taskId, executionId);
                return TaskExecutionOutcome.ACKNOWLEDGE;
            }
            return TaskExecutionOutcome.DEAD_LETTER;
        } catch (RuntimeException persistenceException) {
            LOGGER.error("event=task_failure_persistence_error taskId={} errorCode={}",
                    taskId, errorCode, persistenceException);
            return TaskExecutionOutcome.DEAD_LETTER;
        }
    }

    private boolean save(long taskId, java.util.UUID executionId, TaskExecutionResult result) {
        if (result instanceof TaskExecutionResult.Completed(Object completed)) {
            JsonNode json = objectMapper.valueToTree(completed);
            return persistenceService.complete(taskId, executionId, json);
        } else if (result instanceof TaskExecutionResult.Failed(TaskErrorCode errorCode, String errorMessage)) {
            return persistenceService.fail(taskId, executionId, errorCode, errorMessage);
        }
        throw new IllegalStateException("Unsupported task execution result: " + result);
    }
}
