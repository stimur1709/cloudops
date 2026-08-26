package com.github.stimur1709.cloudops.organization.api;

import java.net.URI;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.organization.application.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = OrganizationResponse.from(organizationService.create(request.name()));
        return ResponseEntity.created(URI.create("/api/organizations/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable long id) {
        return OrganizationResponse.from(organizationService.get(id));
    }

    @PostMapping("/search")
    public SearchResponse<OrganizationResponse> search(@Valid @RequestBody SearchRequest request) {
        return SearchResponse.from(organizationService.search(request.toQuery()), OrganizationResponse::from);
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdateOrganizationRequest request
    ) {
        return OrganizationResponse.from(organizationService.update(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
