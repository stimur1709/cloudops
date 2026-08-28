package com.github.stimur1709.cloudops.monitoring.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceHealthEventJpaRepository extends JpaRepository<ResourceHealthEventEntity, Long> {
}
