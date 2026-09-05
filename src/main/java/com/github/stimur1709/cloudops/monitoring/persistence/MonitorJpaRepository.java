package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorJpaRepository extends JpaRepository<MonitorEntity, Long> {

    List<MonitorEntity> findAllByResourceIdOrderById(long resourceId);

    @Query("""
            SELECT monitor.type AS type, monitor.compatible AS compatible, monitor.healthStatus AS healthStatus
            FROM MonitorEntity monitor WHERE monitor.resourceId = :resourceId
            """)
    List<MonitorHealth> findHealthByResourceId(@Param("resourceId") long resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT monitor FROM MonitorEntity monitor WHERE monitor.resourceId = :resourceId AND monitor.type = :type")
    Optional<MonitorEntity> findByResourceIdAndTypeForUpdate(long resourceId, ProbeType type);

    @Modifying
    @Query(value = """
            INSERT INTO monitors (resource_id, type, next_run_at)
            VALUES (:resourceId, :type, :nextRunAt)
            ON CONFLICT (resource_id, type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("resourceId") long resourceId, @Param("type") String type, @Param("nextRunAt") Instant nextRunAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT monitor FROM MonitorEntity monitor WHERE monitor.id = :id")
    Optional<MonitorEntity> findByIdForUpdate(@Param("id") long id);
}
