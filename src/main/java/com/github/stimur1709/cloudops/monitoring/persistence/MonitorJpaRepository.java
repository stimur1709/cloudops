package com.github.stimur1709.cloudops.monitoring.persistence;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorJpaRepository extends JpaRepository<MonitorEntity, Long> {

    List<MonitorEntity> findAllByResourceIdOrderById(long resourceId);

    boolean existsByResourceIdAndType(long resourceId, ProbeType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select monitor from MonitorEntity monitor where monitor.id = :id")
    Optional<MonitorEntity> findByIdForUpdate(@Param("id") long id);
}
