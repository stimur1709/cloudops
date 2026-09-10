package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityResolver;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/task-capabilities")
public class ResourceTaskCapabilityController {

    private final TaskCapabilityResolver capabilityResolver;

    public ResourceTaskCapabilityController(TaskCapabilityResolver capabilityResolver) {
        this.capabilityResolver = capabilityResolver;
    }

    @GetMapping
    public List<TaskCapabilityResponse> list(@PathVariable long resourceId, Authentication authentication) {
        return capabilityResolver.resolve(resourceId, CurrentUser.id(authentication)).stream()
                .map(TaskCapabilityResponse::from)
                .toList();
    }
}
