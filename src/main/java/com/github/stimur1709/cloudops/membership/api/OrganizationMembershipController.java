package com.github.stimur1709.cloudops.membership.api;

import java.net.URI;
import java.util.List;

import com.github.stimur1709.cloudops.membership.application.OrganizationMembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public List<OrganizationMemberResponse> list(
            @PathVariable long organizationId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Start must not be less than 0") int start,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be greater than 0")
            @Max(value = 100, message = "Size must not be greater than 100") int size
    ) {
        return membershipService.list(organizationId, start, size).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
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
