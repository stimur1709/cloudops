package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.TestTaskTypes;
import java.util.Map;

public class TestTaskHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TestTaskTypes.TYPE;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) {
        return TaskExecutionResult.completed(Map.of("operation", "test"));
    }
}
