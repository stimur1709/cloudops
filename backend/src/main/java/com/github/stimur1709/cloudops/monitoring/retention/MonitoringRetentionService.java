package com.github.stimur1709.cloudops.monitoring.retention;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringRetentionService {

    private final MonitoringResultRetentionRepository retentionRepository;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitoringRetentionService(
            MonitoringResultRetentionRepository retentionRepository, MonitoringProperties properties, Clock clock) {
        this.retentionRepository = retentionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int deleteExpiredBatch() {
        return retentionRepository.deleteExpired(clock.instant(), properties.retentionBatchSize());
    }
}
