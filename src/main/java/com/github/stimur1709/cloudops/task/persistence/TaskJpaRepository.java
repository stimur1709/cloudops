package com.github.stimur1709.cloudops.task.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.status = :running, task.startedAt = :startedAt
             where task.id = :taskId and task.status = :pending
            """)
    int claimPending(
            @Param("taskId") long taskId,
            @Param("startedAt") Instant startedAt,
            @Param("pending") TaskStatus pending,
            @Param("running") TaskStatus running
    );
}
