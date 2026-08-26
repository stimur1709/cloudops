package com.github.stimur1709.cloudops.membership.api;

import java.net.URI;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.membership.application.OrganizationMembershipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/organizations/{organizationId}/members")
public class OrganizationMembershipController {

    private final OrganizationMembershipService membershipService;

    public OrganizationMembershipController(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping
    public ResponseEntity<OrganizationMemberResponse> add(
            @PathVariable long organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request
    ) {
        OrganizationMemberResponse response = OrganizationMemberResponse.from(
                membershipService.add(organizationId, request.userId(), request.role())
        );
        URI location = URI.create("/api/organizations/%d/members/%d"
                .formatted(organizationId, response.userId()));
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/search")
    public SearchResponse<OrganizationMemberResponse> search(
            @PathVariable long organizationId,
            @Valid @RequestBody SearchRequest request
    ) {
        return SearchResponse.from(
                membershipService.search(organizationId, request.toQuery()),
                OrganizationMemberResponse::from
        );
    }

    @PutMapping("/{userId}")
    public OrganizationMemberResponse updateRole(
            @PathVariable long organizationId,
            @PathVariable long userId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request
    ) {
        return OrganizationMemberResponse.from(
                membershipService.updateRole(organizationId, userId, request.role())
        );
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@PathVariable long organizationId, @PathVariable long userId) {
        membershipService.remove(organizationId, userId);
        return ResponseEntity.noContent().build();
    }
}
