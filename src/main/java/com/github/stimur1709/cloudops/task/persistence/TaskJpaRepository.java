package com.github.stimur1709.cloudops.task.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<TaskEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.status = :running, task.startedAt = :startedAt,
                   task.executionId = :executionId, task.leaseExpiresAt = :leaseExpiresAt
             where task.id = :taskId and task.status = :pending
            """)
    int claimPending(
            @Param("taskId") long taskId,
            @Param("startedAt") Instant startedAt,
            @Param("executionId") UUID executionId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("pending") TaskStatus pending,
            @Param("running") TaskStatus running
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.attemptCount = task.attemptCount + 1, task.lastAttemptAt = :attemptedAt
             where task.id = :taskId and task.status = :running and task.executionId = :executionId
            """)
    int recordAttempt(
            @Param("taskId") long taskId,
            @Param("attemptedAt") Instant attemptedAt,
            @Param("executionId") UUID executionId,
            @Param("running") TaskStatus running
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.leaseExpiresAt = :leaseExpiresAt
             where task.id = :taskId and task.status = :running and task.executionId = :executionId
            """)
    int renewLease(
            @Param("taskId") long taskId,
            @Param("executionId") UUID executionId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("running") TaskStatus running
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.status = :completed, task.result = :result, task.completedAt = :completedAt,
                   task.executionId = null, task.leaseExpiresAt = null
             where task.id = :taskId and task.status = :running and task.executionId = :executionId
            """)
    int completeRunning(
            @Param("taskId") long taskId,
            @Param("executionId") UUID executionId,
            @Param("result") tools.jackson.databind.JsonNode result,
            @Param("completedAt") Instant completedAt,
            @Param("running") TaskStatus running,
            @Param("completed") TaskStatus completed
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
               set task.status = :failed, task.errorCode = :errorCode, task.errorMessage = :errorMessage,
                   task.completedAt = :completedAt, task.executionId = null, task.leaseExpiresAt = null
             where task.id = :taskId and task.status = :running and task.executionId = :executionId
            """)
    int failRunning(
            @Param("taskId") long taskId,
            @Param("executionId") UUID executionId,
            @Param("errorCode") TaskErrorCode errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") Instant completedAt,
            @Param("running") TaskStatus running,
            @Param("failed") TaskStatus failed
    );

    @Query(value = """
            SELECT *
              FROM tasks
             WHERE status = 'RUNNING' AND lease_expires_at < :now
             ORDER BY lease_expires_at, id
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<TaskEntity> lockExpired(@Param("now") Instant now, Pageable pageable);

    @Query("select task.status from TaskEntity task where task.id = :taskId")
    TaskStatus findStatus(@Param("taskId") long taskId);
}
