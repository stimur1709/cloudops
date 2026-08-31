package com.github.stimur1709.cloudops.organization.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.organization.application.OrganizationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest request, Authentication authentication) {
        OrganizationResponse response =
                OrganizationResponse.from(organizationService.create(request.name(), CurrentUser.id(authentication)));
        return ResponseEntity.created(URI.create("/api/organizations/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable long id, Authentication authentication) {
        return OrganizationResponse.from(organizationService.get(id, CurrentUser.id(authentication)));
    }

    @PostMapping("/search")
    public SearchResponse<OrganizationResponse> search(
            @Valid @RequestBody SearchRequest request, Authentication authentication) {
        return SearchResponse.from(
                organizationService.search(request.toQuery(), CurrentUser.id(authentication)),
                OrganizationResponse::from);
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            Authentication authentication) {
        return OrganizationResponse.from(
                organizationService.update(id, request.name(), CurrentUser.id(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
        organizationService.delete(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
