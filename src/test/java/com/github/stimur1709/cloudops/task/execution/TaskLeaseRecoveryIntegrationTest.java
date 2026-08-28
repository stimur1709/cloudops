package com.github.stimur1709.cloudops.task.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.application.StaleTaskExecutionException;
import com.github.stimur1709.cloudops.task.application.TaskPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cloudops.task.outbox.enabled=false")
class TaskLeaseRecoveryIntegrationTest {

    private static final UUID OLD_EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskPersistenceService persistenceService;

    @Autowired
    private TaskRecoveryService recoveryService;

    private long organizationId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations
                RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'lease@example.com', 'User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Lease test', now(), now()) RETURNING id
                """, Long.class);
        resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('service', 'SERVICE', 'ACTIVE', ?,
                        CAST('{"url":"https://example.com","expectedStatus":200,"timeoutMs":1000}' AS jsonb),
                        now(), now()) RETURNING id
                """, Long.class, organizationId);
    }

    @Test
    void heartbeatRenewsOnlyMatchingExecution() {
        long taskId = insertPendingTask();
        var claim = persistenceService.claim(taskId);
        Instant originalExpiration = leaseExpiration(taskId);

        assertThat(persistenceService.renewLease(taskId, UUID.randomUUID())).isFalse();
        assertThat(leaseExpiration(taskId)).isEqualTo(originalExpiration);
        assertThat(persistenceService.renewLease(taskId, claim.executionId())).isTrue();
        assertThat(leaseExpiration(taskId)).isAfterOrEqualTo(originalExpiration);
    }

    @Test
    void recoversExpiredTaskAndCreatesNextOutboxGenerationAtomically() {
        long taskId = insertRunningTask(OLD_EXECUTION_ID, 0, 2, Instant.now().minusSeconds(1));

        assertThat(recoveryService.recoverExpired()).isEqualTo(1);

        var state = jdbcTemplate.queryForMap("""
                SELECT status, execution_id, lease_expires_at, started_at, recovery_count, attempt_count
                  FROM tasks WHERE id = ?
                """, taskId);
        assertThat(state)
                .containsEntry("status", "PENDING")
                .containsEntry("execution_id", null)
                .containsEntry("lease_expires_at", null)
                .containsEntry("started_at", null)
                .containsEntry("recovery_count", 1)
                .containsEntry("attempt_count", 2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deduplication_key FROM outbox_messages WHERE aggregate_id = ?", String.class, taskId
        )).isEqualTo("task:%d:execution:1".formatted(taskId));
    }

    @Test
    void rollsBackRecoveryWhenOutboxGenerationAlreadyExists() {
        long taskId = insertRunningTask(OLD_EXECUTION_ID, 0, 0, Instant.now().minusSeconds(1));
        insertOutbox(taskId, "task:%d:execution:1".formatted(taskId));

        assertThatThrownBy(recoveryService::recoverExpired)
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tasks WHERE id = ?", String.class, taskId
        )).isEqualTo("RUNNING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recovery_count FROM tasks WHERE id = ?", Integer.class, taskId
        )).isZero();
    }

    @Test
    void fencesOldExecutionAfterRecoveryAndNewClaim() {
        long taskId = insertRunningTask(OLD_EXECUTION_ID, 0, 0, Instant.now().minusSeconds(1));
        recoveryService.recoverExpired();
        var newClaim = persistenceService.claim(taskId);

        assertThat(newClaim.executionId()).isNotEqualTo(OLD_EXECUTION_ID);
        assertThat(persistenceService.complete(taskId, OLD_EXECUTION_ID,
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode())).isFalse();
        assertThatThrownBy(() -> persistenceService.recordAttempt(taskId, OLD_EXECUTION_ID))
                .isInstanceOf(StaleTaskExecutionException.class);
        assertThat(persistenceService.recordAttempt(taskId, newClaim.executionId())).isEqualTo(1);
    }

    @Test
    void activeTaskIsSkippedAndRecoveryLimitProducesSafeFailure() {
        long activeId = insertRunningTask(OLD_EXECUTION_ID, 0, 0, Instant.now().plusSeconds(60));
        long exhaustedId = insertRunningTask(UUID.randomUUID(), 3, 1, Instant.now().minusSeconds(1));

        assertThat(recoveryService.recoverExpired()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tasks WHERE id = ?", String.class, activeId
        )).isEqualTo("RUNNING");
        var exhausted = jdbcTemplate.queryForMap("""
                SELECT status, error_code, error_message, execution_id, lease_expires_at
                  FROM tasks WHERE id = ?
                """, exhaustedId);
        assertThat(exhausted)
                .containsEntry("status", "FAILED")
                .containsEntry("error_code", TaskErrorCode.RECOVERY_EXHAUSTED.name())
                .containsEntry("error_message", "Task execution could not be recovered")
                .containsEntry("execution_id", null)
                .containsEntry("lease_expires_at", null);
    }

    @Test
    void concurrentRecoveryJobsHandleTaskOnlyOnce() throws Exception {
        long taskId = insertRunningTask(OLD_EXECUTION_ID, 0, 0, Instant.now().minusSeconds(1));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() -> recoverAfter(barrier));
            Future<Integer> second = executor.submit(() -> recoverAfter(barrier));

            assertThat(Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder(0, 1);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_messages WHERE aggregate_id = ?", Integer.class, taskId
        )).isEqualTo(1);
    }

    private int recoverAfter(CyclicBarrier barrier) throws Exception {
        barrier.await();
        return recoveryService.recoverExpired();
    }

    private long insertPendingTask() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tasks (organization_id, resource_id, type, status, created_by, created_at)
                VALUES (?, ?, 'HTTP_CHECK', 'PENDING', ?, now()) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID);
    }

    private long insertRunningTask(
            UUID executionId, int recoveryCount, int attemptCount, Instant leaseExpiresAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tasks (
                    organization_id, resource_id, type, status, created_by, created_at, started_at,
                    execution_id, lease_expires_at, recovery_count, attempt_count
                )
                VALUES (?, ?, 'HTTP_CHECK', 'RUNNING', ?, now(), now(), ?, ?, ?, ?) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID,
                executionId, leaseExpiresAt.atOffset(ZoneOffset.UTC), recoveryCount, attemptCount);
    }

    private void insertOutbox(long taskId, String deduplicationKey) {
        jdbcTemplate.update("""
                INSERT INTO outbox_messages (
                    id, message_type, aggregate_type, aggregate_id, payload, created_at, deduplication_key
                ) VALUES (?, 'TASK_EXECUTION_REQUESTED', 'TASK', ?, CAST(? AS jsonb), now(), ?)
                """, UUID.randomUUID(), taskId, "{\"taskId\":%d}".formatted(taskId), deduplicationKey);
    }

    private Instant leaseExpiration(long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT lease_expires_at FROM tasks WHERE id = ?", OffsetDateTime.class, taskId
        ).toInstant();
    }
}
