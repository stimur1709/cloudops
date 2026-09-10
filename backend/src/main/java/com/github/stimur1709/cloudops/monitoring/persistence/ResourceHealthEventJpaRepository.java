package com.github.stimur1709.cloudops.monitoring.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceHealthEventJpaRepository extends JpaRepository<ResourceHealthEventEntity, Long> {

    Optional<ResourceHealthEventEntity> findFirstByResourceIdAndChangedAtLessThanEqualOrderByChangedAtDescIdDesc(
            long resourceId, Instant changedAt);

    List<ResourceHealthEventEntity>
            findAllByResourceIdAndChangedAtGreaterThanAndChangedAtLessThanOrderByChangedAtAscIdAsc(
                    long resourceId, Instant from, Instant to);
}
