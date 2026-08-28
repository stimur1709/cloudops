package com.github.stimur1709.cloudops.monitoring.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.ResourceAvailabilityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @RequestParam Instant from,
            @RequestParam Instant to,
            Authentication authentication
    ) {
        return ResourceAvailabilityResponse.from(
                availabilityService.get(resourceId, from, to, CurrentUser.id(authentication))
        );
    }
}
