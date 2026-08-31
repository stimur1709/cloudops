package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.task.TaskType;

public record TaskExecutionContext(long resourceId, TaskType type, ResourceConfig resourceConfig) {}
