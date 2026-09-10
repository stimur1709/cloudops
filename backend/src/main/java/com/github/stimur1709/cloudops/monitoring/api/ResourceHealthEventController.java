package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.ResourceHealthEventService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/health/events")
public class ResourceHealthEventController {

    private final ResourceHealthEventService eventService;

    public ResourceHealthEventController(ResourceHealthEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/search")
    public SearchResponse<ResourceHealthEventResponse> search(
            @PathVariable long resourceId, @Valid @RequestBody SearchRequest request, Authentication authentication) {
        return SearchResponse.from(
                eventService.search(resourceId, request.toQuery(), CurrentUser.id(authentication)),
                ResourceHealthEventResponse::from);
    }
}
