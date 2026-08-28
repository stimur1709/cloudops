package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.common.api.search.SearchRequest;
import com.github.stimur1709.cloudops.common.api.search.SearchResponse;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.monitoring.application.MonitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PutMapping("/{id}")
    public MonitorResponse update(
            @PathVariable long id,
            @Valid @RequestBody UpdateMonitorRequest request,
            Authentication authentication
    ) {
        return MonitorResponse.from(monitorService.update(
                id,
                request.intervalSeconds(),
                request.enabled(),
                request.storageMode(),
                request.retentionDays(),
                request.failureThreshold(),
                request.recoveryThreshold(),
                CurrentUser.id(authentication)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
        monitorService.delete(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/results/search")
    public SearchResponse<MonitoringResultResponse> searchResults(
            @PathVariable long id,
            @Valid @RequestBody SearchRequest request,
            Authentication authentication
    ) {
        return SearchResponse.from(
                monitorService.searchResults(id, request.toQuery(), CurrentUser.id(authentication)),
                MonitoringResultResponse::from
        );
    }
}
