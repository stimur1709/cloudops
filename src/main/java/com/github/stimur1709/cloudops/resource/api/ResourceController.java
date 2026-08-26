package com.github.stimur1709.cloudops.resource.api;

import java.net.URI;

import com.github.stimur1709.cloudops.resource.application.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse response = ResourceResponse.from(
                resourceService.create(request.name(), request.type(), request.status())
        );
        URI location = URI.create("/api/resources/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable long id) {
        return ResourceResponse.from(resourceService.get(id));
    }
}

