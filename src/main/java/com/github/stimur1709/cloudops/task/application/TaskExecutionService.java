package com.github.stimur1709.cloudops.task.application;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskPersistenceService persistenceService;
    private final TaskHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;

    public TaskExecutionService(
            TaskPersistenceService persistenceService,
            TaskHandlerRegistry handlerRegistry,
            ObjectMapper objectMapper
    ) {
        this.persistenceService = persistenceService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
    }

    public void execute(long taskId) {
        TaskPersistenceService.ClaimedTask claimed = persistenceService.claim(taskId);
        if (claimed == null) {
            LOGGER.debug("Task {} does not exist or is no longer pending", taskId);
            return;
        }

        TaskExecutionContext context = new TaskExecutionContext(
                claimed.taskId(), claimed.resourceId(), claimed.type(), claimed.resourceConfig()
        );
        TaskExecutionResult result;
        try {
            result = handlerRegistry.get(claimed.type()).execute(context);
        } catch (TaskHandlerNotFoundException exception) {
            LOGGER.error("Cannot execute task {}: {}", taskId, exception.getMessage());
            result = TaskExecutionResult.failed(TaskErrorCode.HANDLER_NOT_FOUND, "Task handler is not configured");
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected error while executing task {}", taskId, exception);
            result = TaskExecutionResult.failed(TaskErrorCode.EXECUTION_ERROR, "Task execution failed unexpectedly");
        }

        save(taskId, result);
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
