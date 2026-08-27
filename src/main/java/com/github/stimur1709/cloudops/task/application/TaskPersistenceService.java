package com.github.stimur1709.cloudops.task.application;

import java.time.Clock;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import com.github.stimur1709.cloudops.task.persistence.TaskJpaRepository;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageEntity;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TaskPersistenceService.class);

    private final TaskJpaRepository taskRepository;
    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final ResourceConfigMapper configMapper;
    private final Clock clock;
    private final OutboxMessageJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TaskPersistenceService(
            TaskJpaRepository taskRepository,
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            ResourceConfigMapper configMapper,
            Clock clock,
            OutboxMessageJpaRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.configMapper = configMapper;
        this.clock = clock;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskEntity create(long resourceId, TaskType type, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);
        requireSupported(type, resource.type());
        if (resource.status() != ResourceStatus.ACTIVE) {
            throw new ConflictException("RESOURCE_INACTIVE", "HTTP check requires an active resource");
        }
        TaskEntity task = TaskEntity.create(
                resource.organizationId(), resource.id(), type, currentUserId, clock.instant()
        );
        taskRepository.saveAndFlush(task);
        var payload = objectMapper.createObjectNode().put("taskId", task.id());
        OutboxMessageEntity message = OutboxMessageEntity.taskExecutionRequested(task.id(), payload, clock.instant());
        outboxRepository.saveAndFlush(message);
        log.info("Created outbox message: messageId={}, messageType={}, aggregateId={}",
                message.id(), message.messageType(), message.aggregateId());
        return task;
    }

    @Transactional
    public ClaimedTask claim(long taskId) {
        int updated = taskRepository.claimPending(
                taskId, clock.instant(), TaskStatus.PENDING, TaskStatus.RUNNING
        );
        if (updated == 0) {
            return null;
        }
        TaskEntity task = taskRepository.findById(taskId).orElseThrow(NotFoundException::new);
        ResourceEntity resource = resourceRepository.findById(task.resourceId()).orElseThrow(NotFoundException::new);
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        return new ClaimedTask(task.id(), task.resourceId(), task.type(), config);
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

    @Transactional
    public int recordAttempt(long taskId) {
        int updated = taskRepository.recordAttempt(taskId, clock.instant(), TaskStatus.RUNNING);
        if (updated != 1) {
            throw new IllegalStateException("Cannot record an attempt for a task that is not running");
        }
        return taskRepository.findById(taskId).orElseThrow(NotFoundException::new).attemptCount();
    }

    @Transactional(readOnly = true)
    public TaskStatus status(long taskId) {
        return taskRepository.findStatus(taskId);
    }

    private void requireSupported(TaskType taskType, ResourceType resourceType) {
        if (taskType != TaskType.HTTP_CHECK || resourceType != ResourceType.SERVICE) {
            throw new ConflictException(
                    "TASK_TYPE_NOT_SUPPORTED",
                    "Task type %s is not supported for resource type %s".formatted(taskType, resourceType)
            );
        }
    }

    public record ClaimedTask(long taskId, long resourceId, TaskType type, ResourceConfig resourceConfig) {
    }
}
