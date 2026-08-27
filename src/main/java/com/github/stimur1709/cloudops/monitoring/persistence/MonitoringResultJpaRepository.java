package com.github.stimur1709.cloudops.monitoring.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringResultJpaRepository extends JpaRepository<MonitoringResultEntity, Long> {

    void deleteAllByMonitorId(long monitorId);
}
