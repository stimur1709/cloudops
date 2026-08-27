package com.github.stimur1709.cloudops.task.application;

import java.time.Clock;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import com.github.stimur1709.cloudops.task.persistence.TaskJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class TaskPersistenceService {

    private final TaskJpaRepository taskRepository;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final ResourceConfigMapper configMapper;
    private final Clock clock;

    public TaskPersistenceService(
            TaskJpaRepository taskRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            ResourceConfigMapper configMapper,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.configMapper = configMapper;
        this.clock = clock;
    }

    @Transactional
    public PreparedHttpCheck create(long resourceId, TaskType type, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        requireSupported(type, resource.type());
        if (resource.status() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_INACTIVE", "HTTP check requires an active resource");
        }
        ServiceResourceConfig config = (ServiceResourceConfig) configMapper.fromJson(resource.type(), resource.config());
        TaskEntity task = TaskEntity.create(
                resource.organizationId(), resource.id(), type, currentUserId, clock.instant()
        );
        taskRepository.saveAndFlush(task);
        return new PreparedHttpCheck(task.id(), config);
    }

    @Transactional
    public void start(long taskId) {
        TaskEntity task = taskRepository.findById(taskId).orElseThrow(NotFoundException::new);
        task.start(clock.instant());
        taskRepository.flush();
    }

    @Transactional
    public TaskEntity complete(long taskId, JsonNode result) {
        TaskEntity task = taskRepository.findById(taskId).orElseThrow(NotFoundException::new);
        task.complete(result, clock.instant());
        taskRepository.flush();
        return task;
    }

    @Transactional
    public TaskEntity fail(long taskId, TaskErrorCode errorCode, String errorMessage) {
        TaskEntity task = taskRepository.findById(taskId).orElseThrow(NotFoundException::new);
        task.fail(errorCode, errorMessage, clock.instant());
        taskRepository.flush();
        return task;
    }

    private void requireSupported(TaskType taskType, ResourceType resourceType) {
        if (taskType != TaskType.HTTP_CHECK || resourceType != ResourceType.SERVICE) {
            throw new ConflictException(
                    "TASK_TYPE_NOT_SUPPORTED",
                    "Task type %s is not supported for resource type %s".formatted(taskType, resourceType)
            );
        }
    }

    public record PreparedHttpCheck(long taskId, ServiceResourceConfig config) {
    }
}
