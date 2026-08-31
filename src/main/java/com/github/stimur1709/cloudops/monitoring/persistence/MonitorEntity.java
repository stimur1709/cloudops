package com.github.stimur1709.cloudops.monitoring.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProbeType type;

    @Column(nullable = false)
    private boolean compatible;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_result", columnDefinition = "jsonb")
    private JsonNode lastResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 10)
    private HealthStatus healthStatus;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    protected MonitorEntity() {
    }

    private MonitorEntity(
            long resourceId,
            ProbeType type,
            Instant nextRunAt
    ) {
        this.resourceId = resourceId;
        this.type = type;
        this.compatible = true;
        this.nextRunAt = nextRunAt;
        this.healthStatus = HealthStatus.UNKNOWN;
    }

    public static MonitorEntity create(
            long resourceId,
            ProbeType type,
            Instant nextRunAt
    ) {
        return new MonitorEntity(resourceId, type, nextRunAt);
    }

    public void record(Instant checkedAt, JsonNode result, boolean success, int failureThreshold, int recoveryThreshold) {
        lastCheckedAt = checkedAt;
        lastResult = result;
        updateHealth(success, failureThreshold, recoveryThreshold);
    }

    public void scheduleNow(Instant now) {
        nextRunAt = now;
    }

    public void setCompatible(boolean compatible, Instant now) {
        if (compatible && !this.compatible) {
            nextRunAt = now;
        }
        this.compatible = compatible;
    }

    private void updateHealth(boolean success, int failureThreshold, int recoveryThreshold) {
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
    public boolean compatible() { return compatible; }
    public Instant nextRunAt() { return nextRunAt; }
    public Instant lastCheckedAt() { return lastCheckedAt; }
    public JsonNode lastResult() { return lastResult; }
    public HealthStatus healthStatus() { return healthStatus; }
    public int consecutiveFailures() { return consecutiveFailures; }
    public int consecutiveSuccesses() { return consecutiveSuccesses; }
}
