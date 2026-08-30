package com.github.stimur1709.cloudops.task.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.stimur1709.cloudops.task.TaskType;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final Map<TaskType, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        Map<TaskType, TaskHandler> indexed = new HashMap<>();
        for (TaskHandler handler : handlers) {
            if (indexed.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Multiple task handlers are configured for " + handler.type());
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public boolean supports(TaskType type) {
        return handlers.containsKey(type);
    }

    public void requireSupported(TaskType type) {
        if (!handlers.containsKey(type)) {
            throw new TaskHandlerNotFoundException(type);
        }
    }

    public TaskHandler get(TaskType type) {
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new TaskHandlerNotFoundException(type);
        }
        return handler;
    }
}
