package com.github.stimur1709.cloudops.task.capability;

import java.util.List;

public record TaskCapabilityAssessment(boolean supported, List<TaskCapabilityReason> reasons) {

    public TaskCapabilityAssessment {
        reasons = List.copyOf(reasons);
    }
}
