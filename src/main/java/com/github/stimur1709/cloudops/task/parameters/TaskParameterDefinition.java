package com.github.stimur1709.cloudops.task.parameters;

import com.github.stimur1709.cloudops.task.TaskType;

public interface TaskParameterDefinition<P extends TaskParameters> {

    TaskType type();

    Class<P> parametersType();
}
