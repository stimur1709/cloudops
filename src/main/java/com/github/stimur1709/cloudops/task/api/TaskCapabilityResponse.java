package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.capability.TaskCapability;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityReason;
import java.util.List;

public record TaskCapabilityResponse(
        TaskType type, boolean supported, boolean available, boolean allowed, List<TaskCapabilityReason> reasons) {

    static TaskCapabilityResponse from(TaskCapability capability) {
        return new TaskCapabilityResponse(
                capability.type(),
                capability.supported(),
                capability.available(),
                capability.allowed(),
                capability.reasons());
    }
}
