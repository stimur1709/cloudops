package com.github.stimur1709.cloudops.monitoring.retention;

import java.time.Clock;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.EffectiveProbeSettingsBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringRetentionService {

    private final EffectiveProbeSettingsBatchRepository batchRepository;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitoringRetentionService(
            EffectiveProbeSettingsBatchRepository batchRepository,
            MonitoringProperties properties,
            Clock clock
    ) {
        this.batchRepository = batchRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int deleteExpiredBatch() {
        return batchRepository.deleteExpired(clock.instant(), properties.retentionBatchSize());
    }
}
