package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.task.application.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable long id, Authentication authentication) {
        return TaskResponse.from(taskService.get(id, CurrentUser.id(authentication)));
    }

    @PostMapping("/search")
    public SearchResponse<TaskResponse> search(
            @Valid @RequestBody SearchRequest request,
            Authentication authentication
    ) {
        return SearchResponse.from(
                taskService.search(request.toQuery(), CurrentUser.id(authentication)),
                TaskResponse::from
        );
    }
}
