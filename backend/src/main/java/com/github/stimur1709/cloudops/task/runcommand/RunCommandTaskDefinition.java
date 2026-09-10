package com.github.stimur1709.cloudops.task.runcommand;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.parameters.TaskParameterDefinition;
import org.springframework.stereotype.Component;

@Component
public class RunCommandTaskDefinition implements TaskParameterDefinition<RunCommandParameters> {

    @Override
    public TaskType type() {
        return TaskType.RUN_COMMAND;
    }

    @Override
    public Class<RunCommandParameters> parametersType() {
        return RunCommandParameters.class;
    }
}
