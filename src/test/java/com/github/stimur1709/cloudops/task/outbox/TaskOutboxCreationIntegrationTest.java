package com.github.stimur1709.cloudops.task.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.task.application.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cloudops.task.outbox.enabled=false")
class TaskOutboxCreationIntegrationTest {

    @Autowired
    private TaskService taskService;

    @MockitoBean
    private TaskExecutionCommandPublisher publisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private long resourceId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY");
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
                VALUES ('server', 'SERVER', 'ACTIVE', ?,
                        CAST('{"host":"127.0.0.1","sshPort":22}' AS jsonb),
                        now(), now()) RETURNING id
                """, Long.class, organizationId);
        long credentialId = jdbcTemplate.queryForObject("""
                INSERT INTO credentials
                    (organization_id, name, type, username, secret_encrypted, created_at, updated_at)
                VALUES (?, 'SSH', 'USERNAME_PASSWORD', 'cloudops', 'unused', now(), now()) RETURNING id
                """, Long.class, organizationId);
        jdbcTemplate.update("""
                INSERT INTO resource_credentials (resource_id, credential_id, purpose) VALUES (?, ?, 'SSH')
                """, resourceId, credentialId);
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_outbox_insert ON outbox_messages");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_outbox_insert()");
    }

    @Test
    void createsTaskAndExplicitCommandPayloadInOneTransaction() {
        var task = taskService.create(
                resourceId,
                com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE,
                parameters(),
                TestAuthentication.USER_ID);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Long.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payload ->> 'taskId' FROM outbox_messages WHERE aggregate_id = ?
                """, String.class, task.id())).isEqualTo(Long.toString(task.id()));
        assertThat(jdbcTemplate.queryForObject("SELECT message_type FROM outbox_messages", String.class))
                .isEqualTo("TASK_EXECUTION_REQUESTED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT published_at FROM outbox_messages", java.time.OffsetDateTime.class))
                .isNull();
    }

    @Test
    void taskCreationDoesNotCallRabbitPublisherOnRequestThread() {
        var task = taskService.create(
                resourceId,
                com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE,
                parameters(),
                TestAuthentication.USER_ID);

        assertThat(task.id()).isPositive();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class))
                .isOne();
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

        assertThatThrownBy(() -> taskService.create(
                        resourceId,
                        com.github.stimur1709.cloudops.task.TestTaskTypes.TYPE,
                        parameters(),
                        TestAuthentication.USER_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_messages", Long.class))
                .isZero();
    }

    private tools.jackson.databind.JsonNode parameters() {
        return objectMapper.createObjectNode().put("command", "true");
    }
}
