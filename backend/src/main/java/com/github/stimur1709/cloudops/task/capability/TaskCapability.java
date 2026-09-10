package com.github.stimur1709.cloudops.task.capability;

import com.github.stimur1709.cloudops.task.TaskType;
import java.util.List;

public record TaskCapability(
        TaskType type, boolean supported, boolean available, boolean allowed, List<TaskCapabilityReason> reasons) {

    public TaskCapability {
        reasons = List.copyOf(reasons);
    }
}
