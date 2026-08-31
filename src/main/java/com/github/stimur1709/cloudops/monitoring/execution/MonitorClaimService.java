package com.github.stimur1709.cloudops.monitoring.execution;

import java.time.Clock;
import java.util.List;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.EffectiveProbeSettingsBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorClaimService {

    private final EffectiveProbeSettingsBatchRepository batchRepository;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitorClaimService(
            EffectiveProbeSettingsBatchRepository batchRepository,
            MonitoringProperties properties,
            Clock clock
    ) {
        this.batchRepository = batchRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<Long> claimDue() {
        return batchRepository.claimDue(clock.instant(), properties.batchSize());
    }
}
