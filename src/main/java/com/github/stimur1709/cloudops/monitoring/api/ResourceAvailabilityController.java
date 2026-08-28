package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.ResourceAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/health/availability")
public class ResourceAvailabilityController {

    private final ResourceAvailabilityService availabilityService;

    public ResourceAvailabilityController(ResourceAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public ResourceAvailabilityResponse get(
            @PathVariable long resourceId,
            @Valid @ModelAttribute ResourceAvailabilityRequest request,
            Authentication authentication
    ) {
        return ResourceAvailabilityResponse.from(
                availabilityService.get(
                        resourceId, request.from(), request.to(), CurrentUser.id(authentication)
                )
        );
    }
}
