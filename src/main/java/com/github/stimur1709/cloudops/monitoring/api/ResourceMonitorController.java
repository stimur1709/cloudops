package com.github.stimur1709.cloudops.monitoring.api;

import java.util.List;

import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.MonitorService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources/{resourceId}/monitors")
public class ResourceMonitorController {

    private final MonitorService monitorService;

    public ResourceMonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public List<MonitorResponse> list(@PathVariable long resourceId, Authentication authentication) {
        return monitorService.list(resourceId, CurrentUser.id(authentication)).stream()
                .map(MonitorResponse::from)
                .toList();
    }
}
