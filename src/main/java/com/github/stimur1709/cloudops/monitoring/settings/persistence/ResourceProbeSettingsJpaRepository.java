package com.github.stimur1709.cloudops.monitoring.settings.persistence;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.probe.ProbeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceProbeSettingsJpaRepository extends JpaRepository<ResourceProbeSettingsEntity, Long> {
    Optional<ResourceProbeSettingsEntity> findByResourceIdAndProbeType(long resourceId, ProbeType probeType);

    List<ResourceProbeSettingsEntity> findAllByResourceId(long resourceId);

    void deleteByResourceIdAndProbeType(long resourceId, ProbeType probeType);
}
