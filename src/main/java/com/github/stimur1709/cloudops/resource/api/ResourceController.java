package com.github.stimur1709.cloudops.resource.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.resource.application.ResourceDetails;
import com.github.stimur1709.cloudops.resource.application.ResourceService;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;
    private final ResourceConfigMapper configMapper;

    public ResourceController(ResourceService resourceService, ResourceConfigMapper configMapper) {
        this.resourceService = resourceService;
        this.configMapper = configMapper;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(
            @Valid @RequestBody CreateResourceRequest request, Authentication authentication) {
        ResourceDetails resource = resourceService.create(
                request.name(),
                request.type(),
                request.status(),
                request.organizationId(),
                request.config(),
                CurrentUser.id(authentication));
        ResourceResponse response = ResourceResponse.from(resource, configMapper);
        URI location = URI.create("/api/resources/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable long id, Authentication authentication) {
        return ResourceResponse.from(resourceService.get(id, CurrentUser.id(authentication)), configMapper);
    }

    @PostMapping("/search")
    public SearchResponse<ResourceResponse> search(
            @Valid @RequestBody SearchRequest request, Authentication authentication) {
        return SearchResponse.from(
                resourceService.search(request.toQuery(), CurrentUser.id(authentication)),
                resource -> ResourceResponse.from(resource, configMapper));
    }

    @PutMapping("/{id}")
    public ResourceResponse update(
            @PathVariable long id, @Valid @RequestBody UpdateResourceRequest request, Authentication authentication) {
        ResourceDetails resource = resourceService.update(
                id,
                request.name(),
                request.type(),
                request.status(),
                request.organizationId(),
                request.config(),
                CurrentUser.id(authentication));
        return ResourceResponse.from(resource, configMapper);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
        resourceService.delete(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
