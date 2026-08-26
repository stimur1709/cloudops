package com.github.stimur1709.cloudops.resource.api;

import java.net.URI;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.resource.application.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/search")
    public SearchResponse<ResourceResponse> search(@Valid @RequestBody SearchRequest request) {
        return SearchResponse.from(resourceService.search(request.toQuery()), ResourceResponse::from);
    }

    @PutMapping("/{id}")
    public ResourceResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdateResourceRequest request
    ) {
        return ResourceResponse.from(
                resourceService.update(id, request.name(), request.type(), request.status())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
