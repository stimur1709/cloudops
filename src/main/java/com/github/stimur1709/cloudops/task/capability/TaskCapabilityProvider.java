package com.github.stimur1709.cloudops.task.capability;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.task.TaskType;

public interface TaskCapabilityProvider {

    TaskType type();

    TaskCapabilityAssessment assess(ResourceEntity resource, ResourceConfig config);

    boolean allowed(MembershipRole role);
}
