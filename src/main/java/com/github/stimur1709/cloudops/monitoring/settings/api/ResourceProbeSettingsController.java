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
@RequestMapping("/api/resources/{resourceId}/monitoring-settings")
public class ResourceProbeSettingsController {

    private final ProbeSettingsService service;

    public ResourceProbeSettingsController(ProbeSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProbeSettingsResponse> list(@PathVariable long resourceId, Authentication auth) {
        return service.listResource(resourceId, CurrentUser.id(auth));
    }

    @PutMapping("/{probeType}")
    public ProbeSettingsResponse put(@PathVariable long resourceId,
                                     @PathVariable ProbeType probeType, @Valid @RequestBody ProbeSettingsRequest request, Authentication auth) {
        return service.putResource(resourceId, probeType, request, CurrentUser.id(auth));
    }

    @DeleteMapping("/{probeType}")
    public ResponseEntity<Void> delete(@PathVariable long resourceId,
                                       @PathVariable ProbeType probeType, Authentication auth) {
        service.deleteResource(resourceId, probeType, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }
}
