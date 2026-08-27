package com.github.stimur1709.cloudops.task.api;

import java.net.URI;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.task.application.TaskService;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/tasks")
public class ResourceTaskController {

    private final TaskService taskService;

    public ResourceTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> run(
            @PathVariable long resourceId,
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication
    ) {
        TaskEntity task = taskService.create(resourceId, request.type(), CurrentUser.id(authentication));
        return ResponseEntity.accepted()
                .location(URI.create("/api/tasks/" + task.id()))
                .body(TaskResponse.from(task));
    }
}
