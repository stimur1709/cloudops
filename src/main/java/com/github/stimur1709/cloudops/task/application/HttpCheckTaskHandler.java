package com.github.stimur1709.cloudops.task.application;

import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import com.github.stimur1709.cloudops.task.TaskType;
import org.springframework.stereotype.Component;

@Component
public class HttpCheckTaskHandler implements TaskHandler {

    private final HttpCheckClient httpCheckClient;

    public HttpCheckTaskHandler(HttpCheckClient httpCheckClient) {
        this.httpCheckClient = httpCheckClient;
    }

    @Override
    public TaskType supports() {
        return TaskType.HTTP_CHECK;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) {
        if (!(context.resourceConfig() instanceof ServiceResourceConfig config)) {
            throw new IllegalArgumentException("HTTP_CHECK requires SERVICE resource configuration");
        }
        HttpCheckOutcome outcome = httpCheckClient.execute(config);
        if (outcome.successfulCall()) {
            return TaskExecutionResult.completed(outcome.result());
        }
        return TaskExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }
}
