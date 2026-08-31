package com.github.stimur1709.cloudops.monitoring.settings.api;

import java.util.List;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.settings.application.ProbeSettingsService;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations/{organizationId}/monitoring-settings")
public class OrganizationProbeSettingsController {

    private final ProbeSettingsService service;

    public OrganizationProbeSettingsController(ProbeSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProbeSettingsResponse> list(@PathVariable long organizationId, Authentication auth) {
        return service.listOrganization(organizationId, CurrentUser.id(auth));
    }

    @PutMapping("/{probeType}")
    public ProbeSettingsResponse put(@PathVariable long organizationId,
                                     @PathVariable ProbeType probeType, @Valid @RequestBody ProbeSettingsRequest request, Authentication auth) {
        return service.putOrganization(organizationId, probeType, request, CurrentUser.id(auth));
    }

    @DeleteMapping("/{probeType}")
    public ResponseEntity<Void> delete(@PathVariable long organizationId,
                                       @PathVariable ProbeType probeType, Authentication auth) {
        service.deleteOrganization(organizationId, probeType, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }
}
