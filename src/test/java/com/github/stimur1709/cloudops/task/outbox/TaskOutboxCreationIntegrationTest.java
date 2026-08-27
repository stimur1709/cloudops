package com.github.stimur1709.cloudops.task.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.application.TaskPersistenceService;
import com.github.stimur1709.cloudops.task.application.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cloudops.task.outbox.enabled=false")
class TaskOutboxCreationIntegrationTest {

    @Autowired
    private TaskPersistenceService taskPersistenceService;

    @Autowired
    private TaskService taskService;

    @MockitoBean
    private TaskExecutionCommandPublisher publisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long resourceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE monitoring_results, monitors, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'outbox@example.com', 'Outbox User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        long organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Outbox organization', now(), now()) RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO organization_memberships (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', now(), now())
                """, organizationId, TestAuthentication.USER_ID);
        resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('service', 'SERVICE', 'ACTIVE', ?,
                        CAST('{"url":"http://localhost/","expectedStatus":200,"timeoutMs":1000}' AS jsonb),
                        now(), now()) RETURNING id
                """, Long.class, organizationId);
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_outbox_insert ON outbox_messages");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_outbox_insert()");
    }

    @Test
    void createsTaskAndExplicitCommandPayloadInOneTransaction() {
        var task = taskPersistenceService.create(resourceId, TaskType.HTTP_CHECK, TestAuthentication.USER_ID);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payload ->> 'taskId' FROM outbox_messages WHERE aggregate_id = ?
                """, String.class, task.id())).isEqualTo(Long.toString(task.id()));
        assertThat(jdbcTemplate.queryForObject("SELECT message_type FROM outbox_messages", String.class))
                .isEqualTo("TASK_EXECUTION_REQUESTED");
        assertThat(jdbcTemplate.queryForObject("SELECT published_at FROM outbox_messages", java.time.OffsetDateTime.class))
                .isNull();
    }

    @Test
    void taskCreationDoesNotCallRabbitPublisherOnRequestThread() {
        var task = taskService.create(resourceId, TaskType.HTTP_CHECK, TestAuthentication.USER_ID);

        assertThat(task.id()).isPositive();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class)).isOne();
        verifyNoInteractions(publisher);
    }

    @Test
    void outboxInsertFailureRollsBackTask() {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_outbox_insert() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'simulated outbox failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_outbox_insert BEFORE INSERT ON outbox_messages
                FOR EACH ROW EXECUTE FUNCTION reject_outbox_insert()
                """);

        assertThatThrownBy(() -> taskPersistenceService.create(
                resourceId, TaskType.HTTP_CHECK, TestAuthentication.USER_ID
        )).isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class)).isZero();
    }
}
