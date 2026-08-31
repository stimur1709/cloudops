package com.github.stimur1709.cloudops.monitoring.settings.persistence;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.settings.ProbeSettings;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.persistence.*;

@Entity
@Table(name = "organization_probe_settings", uniqueConstraints =
@UniqueConstraint(name = "organization_probe_settings_key", columnNames = {"organization_id", "probe_type"}))
public class OrganizationProbeSettingsEntity implements ProbeSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "probe_type", nullable = false, length = 30)
    private ProbeType probeType;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;
    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;
    @Column(name = "recovery_threshold", nullable = false)
    private int recoveryThreshold;
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_mode", nullable = false, length = 20)
    private StorageMode storageMode;
    @Column(name = "retention_days")
    private Integer retentionDays;
    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    protected OrganizationProbeSettingsEntity() {
    }

    public static OrganizationProbeSettingsEntity create(long organizationId, ProbeType probeType, ProbeSettings s) {
        var entity = new OrganizationProbeSettingsEntity();
        entity.organizationId = organizationId;
        entity.probeType = probeType;
        entity.update(s);
        return entity;
    }

    public void update(ProbeSettings s) {
        enabled = s.enabled();
        intervalSeconds = s.intervalSeconds();
        failureThreshold = s.failureThreshold();
        recoveryThreshold = s.recoveryThreshold();
        storageMode = s.storageMode();
        retentionDays = s.retentionDays();
        timeoutMs = s.timeoutMs();
    }

    public Long id() {
        return id;
    }

    public Long organizationId() {
        return organizationId;
    }

    public ProbeType probeType() {
        return probeType;
    }

    public boolean enabled() {
        return enabled;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public int failureThreshold() {
        return failureThreshold;
    }

    public int recoveryThreshold() {
        return recoveryThreshold;
    }

    public StorageMode storageMode() {
        return storageMode;
    }

    public Integer retentionDays() {
        return retentionDays;
    }

    public Integer timeoutMs() {
        return timeoutMs;
    }
}
