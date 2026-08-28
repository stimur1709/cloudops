package com.github.stimur1709.cloudops.monitoring.api;

import java.net.URI;
import java.util.List;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.MonitorService;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/monitors")
public class ResourceMonitorController {

    private final MonitorService monitorService;

    public ResourceMonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> create(
            @PathVariable long resourceId,
            @Valid @RequestBody CreateMonitorRequest request,
            Authentication authentication
    ) {
        MonitorEntity monitor = monitorService.create(
                resourceId,
                request,
                CurrentUser.id(authentication)
        );
        return ResponseEntity.created(URI.create("/api/monitors/" + monitor.id()))
                .body(MonitorResponse.from(monitor));
    }

    @GetMapping
    public List<MonitorResponse> list(@PathVariable long resourceId, Authentication authentication) {
        return monitorService.list(resourceId, CurrentUser.id(authentication)).stream()
                .map(MonitorResponse::from)
                .toList();
    }
}
