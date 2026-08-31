package com.github.stimur1709.cloudops.monitoring.settings.persistence;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.probe.ProbeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationProbeSettingsJpaRepository extends JpaRepository<OrganizationProbeSettingsEntity, Long> {
    Optional<OrganizationProbeSettingsEntity> findByOrganizationIdAndProbeType(long organizationId, ProbeType probeType);

    List<OrganizationProbeSettingsEntity> findAllByOrganizationId(long organizationId);

    void deleteByOrganizationIdAndProbeType(long organizationId, ProbeType probeType);
}
