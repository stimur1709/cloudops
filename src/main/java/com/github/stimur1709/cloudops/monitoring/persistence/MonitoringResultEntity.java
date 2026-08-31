package com.github.stimur1709.cloudops.monitoring.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "monitoring_results")
public class MonitoringResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode result;

    protected MonitoringResultEntity() {}

    private MonitoringResultEntity(long monitorId, Instant checkedAt, JsonNode result) {
        this.monitorId = monitorId;
        this.checkedAt = checkedAt;
        this.result = result;
    }

    public static MonitoringResultEntity create(long monitorId, Instant checkedAt, JsonNode result) {
        return new MonitoringResultEntity(monitorId, checkedAt, result);
    }

    public Long id() {
        return id;
    }

    public Long monitorId() {
        return monitorId;
    }

    public Instant checkedAt() {
        return checkedAt;
    }

    public JsonNode result() {
        return result;
    }
}
