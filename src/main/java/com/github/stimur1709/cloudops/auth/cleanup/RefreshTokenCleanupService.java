package com.github.stimur1709.cloudops.auth.cleanup;

import com.github.stimur1709.cloudops.auth.config.RefreshTokenProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RefreshTokenCleanupService {

    private final RefreshTokenCleanupRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    RefreshTokenCleanupService(
            RefreshTokenCleanupRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    int deleteBatch() {
        Instant now = clock.instant();
        return repository.deleteBatch(now, now.minus(properties.revokedRetention()), properties.cleanupBatchSize());
    }
}
