package com.github.stimur1709.cloudops.task.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.github.stimur1709.cloudops.task.TaskType;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final Map<TaskType, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        Map<TaskType, TaskHandler> registered = new EnumMap<>(TaskType.class);
        for (TaskHandler handler : handlers) {
            TaskHandler duplicate = registered.putIfAbsent(handler.supports(), handler);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple TaskHandler beans support task type " + handler.supports()
                );
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public TaskHandler get(TaskType type) {
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new TaskHandlerNotFoundException(type);
        }
        return handler;
    }
}
