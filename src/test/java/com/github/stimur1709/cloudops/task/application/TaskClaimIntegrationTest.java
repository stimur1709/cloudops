package com.github.stimur1709.cloudops.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TaskClaimIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskPersistenceService persistenceService;

    private long taskId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'claim@example.com', 'User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        long organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Claim test', now(), now()) RETURNING id
                """, Long.class);
        long resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('service', 'SERVICE', 'ACTIVE', ?,
                        CAST('{"url":"https://example.com","expectedStatus":200,"timeoutMs":1000}' AS jsonb),
                        now(), now()) RETURNING id
                """, Long.class, organizationId);
        taskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (organization_id, resource_id, type, status, created_by, created_at)
                VALUES (?, ?, 'HTTP_CHECK', 'PENDING', ?, now()) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID);
    }

    @Test
    void onlyOneConcurrentConsumerClaimsPendingTask() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<TaskPersistenceService.ClaimedTask> first = executor.submit(() -> claimAfter(barrier));
            Future<TaskPersistenceService.ClaimedTask> second = executor.submit(() -> claimAfter(barrier));

            var claims = Arrays.asList(first.get(), second.get());

            assertThat(claims).filteredOn(task -> task != null).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM tasks WHERE id = ?", String.class, taskId
            )).isEqualTo("RUNNING");
        }
    }

    private TaskPersistenceService.ClaimedTask claimAfter(CyclicBarrier barrier) throws Exception {
        barrier.await();
        return persistenceService.claim(taskId);
    }
}
