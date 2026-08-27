package com.github.stimur1709.cloudops.task.application;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipScopes;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity_;
import com.github.stimur1709.cloudops.task.persistence.TaskJpaRepository;
import com.github.stimur1709.cloudops.task.persistence.TaskSearchDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskService {

    private final TaskPersistenceService persistenceService;
    private final HttpCheckClient httpCheckClient;
    private final TaskJpaRepository taskRepository;
    private final OrganizationAuthorization authorization;
    private final JpaSearchService searchService;
    private final ObjectMapper objectMapper;

    public TaskService(
            TaskPersistenceService persistenceService,
            HttpCheckClient httpCheckClient,
            TaskJpaRepository taskRepository,
            OrganizationAuthorization authorization,
            JpaSearchService searchService,
            ObjectMapper objectMapper
    ) {
        this.persistenceService = persistenceService;
        this.httpCheckClient = httpCheckClient;
        this.taskRepository = taskRepository;
        this.authorization = authorization;
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    public TaskEntity run(long resourceId, TaskType type, long currentUserId) {
        TaskPersistenceService.PreparedHttpCheck prepared = persistenceService.create(resourceId, type, currentUserId);
        persistenceService.start(prepared.taskId());
        HttpCheckOutcome outcome = httpCheckClient.execute(prepared.config());
        if (outcome.successfulCall()) {
            JsonNode result = objectMapper.valueToTree(outcome.result());
            return persistenceService.complete(prepared.taskId(), result);
        }
        return persistenceService.fail(prepared.taskId(), outcome.errorCode(), outcome.errorMessage());
    }

    @Transactional(readOnly = true)
    public TaskEntity get(long id, long currentUserId) {
        TaskEntity task = taskRepository.findById(id).orElseThrow(NotFoundException::new);
        authorization.requireMember(task.organizationId(), currentUserId);
        return task;
    }

    @Transactional(readOnly = true)
    public SearchResult<TaskEntity> search(SearchQuery query, long currentUserId) {
        return searchService.search(
                query,
                OrganizationMembershipScopes.visibleTo(currentUserId, TaskEntity_.organizationId),
                TaskSearchDefinition.DEFINITION
        );
    }
}
