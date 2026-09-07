package com.github.stimur1709.cloudops.auth.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cloudops.security.refresh",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenCleanupService service;

    public RefreshTokenCleanupScheduler(RefreshTokenCleanupService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${cloudops.security.refresh.cleanup-interval:1h}")
    public void clean() {
        service.deleteBatch();
    }
}
