package com.github.stimur1709.cloudops.monitoring.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "monitors")
public class MonitorEntity {

    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    public static final int DEFAULT_RECOVERY_THRESHOLD = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProbeType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_mode", nullable = false, length = 20)
    private StorageMode storageMode;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_result", columnDefinition = "jsonb")
    private JsonNode lastResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 10)
    private HealthStatus healthStatus;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "recovery_threshold", nullable = false)
    private int recoveryThreshold;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    protected MonitorEntity() {
    }

    private MonitorEntity(
            long resourceId,
            ProbeType type,
            boolean enabled,
            int intervalSeconds,
            Instant nextRunAt,
            StorageMode storageMode,
            Integer retentionDays,
            int failureThreshold,
            int recoveryThreshold
    ) {
        this.resourceId = resourceId;
        this.type = type;
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds;
        this.nextRunAt = nextRunAt;
        this.storageMode = storageMode;
        this.retentionDays = retentionDays;
        this.healthStatus = HealthStatus.UNKNOWN;
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
    }

    public static MonitorEntity create(
            long resourceId,
            ProbeType type,
            boolean enabled,
            int intervalSeconds,
            Instant nextRunAt,
            StorageMode storageMode,
            Integer retentionDays,
            int failureThreshold,
            int recoveryThreshold
    ) {
        return new MonitorEntity(
                resourceId, type, enabled, intervalSeconds, nextRunAt, storageMode, retentionDays,
                failureThreshold, recoveryThreshold
        );
    }

    public void update(
            boolean enabled,
            int intervalSeconds,
            StorageMode storageMode,
            Integer retentionDays,
            int failureThreshold,
            int recoveryThreshold,
            Instant now
    ) {
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds;
        this.storageMode = storageMode;
        this.retentionDays = retentionDays;
        if (this.failureThreshold != failureThreshold || this.recoveryThreshold != recoveryThreshold) {
            consecutiveFailures = 0;
            consecutiveSuccesses = 0;
        }
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        if (enabled && nextRunAt.isBefore(now)) {
            nextRunAt = now;
        }
    }

    public void record(Instant checkedAt, JsonNode result, boolean success) {
        lastCheckedAt = checkedAt;
        lastResult = result;
        updateHealth(success);
    }

    private void updateHealth(boolean success) {
        switch (healthStatus) {
            case UNKNOWN -> {
                healthStatus = success ? HealthStatus.UP : HealthStatus.DOWN;
                resetCounters();
            }
            case UP -> {
                consecutiveSuccesses = 0;
                if (success) {
                    consecutiveFailures = 0;
                } else if (++consecutiveFailures >= failureThreshold) {
                    healthStatus = HealthStatus.DOWN;
                    resetCounters();
                }
            }
            case DOWN -> {
                consecutiveFailures = 0;
                if (!success) {
                    consecutiveSuccesses = 0;
                } else if (++consecutiveSuccesses >= recoveryThreshold) {
                    healthStatus = HealthStatus.UP;
                    resetCounters();
                }
            }
        }
    }

    private void resetCounters() {
        consecutiveFailures = 0;
        consecutiveSuccesses = 0;
    }

    public Long id() { return id; }
    public Long resourceId() { return resourceId; }
    public ProbeType type() { return type; }
    public boolean enabled() { return enabled; }
    public int intervalSeconds() { return intervalSeconds; }
    public Instant nextRunAt() { return nextRunAt; }
    public StorageMode storageMode() { return storageMode; }
    public Integer retentionDays() { return retentionDays; }
    public Instant lastCheckedAt() { return lastCheckedAt; }
    public JsonNode lastResult() { return lastResult; }
    public HealthStatus healthStatus() { return healthStatus; }
    public int failureThreshold() { return failureThreshold; }
    public int recoveryThreshold() { return recoveryThreshold; }
    public int consecutiveFailures() { return consecutiveFailures; }
    public int consecutiveSuccesses() { return consecutiveSuccesses; }
}
