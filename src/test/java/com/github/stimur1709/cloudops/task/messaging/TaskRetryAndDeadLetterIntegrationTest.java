package com.github.stimur1709.cloudops.task.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.task.TestTaskTypes;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionContext;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionResult;
import com.github.stimur1709.cloudops.task.execution.TaskHandler;
import com.github.stimur1709.cloudops.task.execution.TaskHandlerNotFoundException;
import com.github.stimur1709.cloudops.task.execution.TaskHandlerRegistry;
import com.github.stimur1709.cloudops.task.execution.RetryableTaskExecutionException;
import com.github.stimur1709.cloudops.task.outbox.TaskExecutionCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TaskRetryAndDeadLetterIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TaskExecutionCommandPublisher commandPublisher;

    @Autowired
    private TaskMessagingProperties messagingProperties;

    @MockitoBean
    private TaskHandlerRegistry handlerRegistry;

    private TaskHandler handler;
    private long organizationId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        reset(handlerRegistry);
        handler = org.mockito.Mockito.mock(TaskHandler.class);
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(messagingProperties.queue());
            channel.queuePurge(messagingProperties.deadLetterQueue());
            return null;
        });
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations
                RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'retry@example.com', 'Retry User', '{noop}unused', now(), now())
                """, TestAuthentication.USER_ID);
        organizationId = jdbcTemplate.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Retry organization', now(), now()) RETURNING id
                """, Long.class);
        resourceId = jdbcTemplate.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('service', 'SERVICE', 'ACTIVE', ?,
                        CAST('{"url":"https://example.com","expectedStatus":200,"timeoutMs":1000}' AS jsonb),
                        now(), now()) RETURNING id
                """, Long.class, organizationId);
    }

    @Test
    void retriesHandlerThenPersistsEveryAttemptAndLastAttemptTime() throws Exception {
        long taskId = insertPendingTask();
        when(handlerRegistry.get(TestTaskTypes.TYPE)).thenReturn(handler);
        when(handler.execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class)))
                .thenThrow(new RetryableTaskExecutionException("temporary"))
                .thenReturn(TaskExecutionResult.completed("ok"));

        publish(taskId);

        awaitStatus(taskId, "COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM tasks WHERE id = ?", Integer.class, taskId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_attempt_at IS NOT NULL FROM tasks WHERE id = ?", Boolean.class, taskId
        )).isTrue();
        verify(handler, times(2)).execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class));
        assertThat(rabbitTemplate.receive(messagingProperties.deadLetterQueue())).isNull();
    }

    @Test
    void exhaustedRetryFailsTaskAndPreservesMessageInDeadLetterQueue() throws Exception {
        long taskId = insertPendingTask();
        when(handlerRegistry.get(TestTaskTypes.TYPE)).thenReturn(handler);
        when(handler.execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class)))
                .thenThrow(new RetryableTaskExecutionException("temporary internal detail"));
        UUID messageId = UUID.randomUUID();

        commandPublisher.publish(messageId, new TaskExecutionCommand(taskId));

        awaitStatus(taskId, "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_code FROM tasks WHERE id = ?", String.class, taskId
        )).isEqualTo("RETRY_EXHAUSTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_message FROM tasks WHERE id = ?", String.class, taskId
        )).isEqualTo("Task execution failed after retry attempts");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM tasks WHERE id = ?", Integer.class, taskId
        )).isEqualTo(3);

        Message deadLetter = awaitDeadLetter();
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo(messageId.toString());
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains("\"taskId\":" + taskId);
    }

    @Test
    void nonRetryableExceptionIsAttemptedOnceAndDeadLettered() throws Exception {
        long taskId = insertPendingTask();
        when(handlerRegistry.get(TestTaskTypes.TYPE)).thenReturn(handler);
        when(handler.execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class)))
                .thenThrow(new IllegalArgumentException("configuration is incompatible"));

        publish(taskId);

        awaitStatus(taskId, "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM tasks WHERE id = ?", Integer.class, taskId
        )).isEqualTo(1);
        verify(handler).execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class));
        assertThat(awaitDeadLetter()).isNotNull();
    }

    @Test
    void missingHandlerFailsWithoutAttemptAndIsDeadLettered() throws Exception {
        long taskId = insertPendingTask();
        when(handlerRegistry.get(TestTaskTypes.TYPE))
                .thenThrow(new TaskHandlerNotFoundException(TestTaskTypes.TYPE));

        publish(taskId);

        awaitStatus(taskId, "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_code FROM tasks WHERE id = ?", String.class, taskId
        )).isEqualTo("HANDLER_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM tasks WHERE id = ?", Integer.class, taskId
        )).isZero();
        assertThat(awaitDeadLetter()).isNotNull();
    }

    @Test
    void malformedMessageIsDeadLetteredAndContainerContinues() throws Exception {
        String invalidPayload = "{not-json";
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId("malformed-message");
        rabbitTemplate.send(
                messagingProperties.exchange(), messagingProperties.routingKey(),
                new Message(invalidPayload.getBytes(StandardCharsets.UTF_8), properties)
        );

        Message deadLetter = awaitDeadLetter();
        assertThat(deadLetter.getBody()).isEqualTo(invalidPayload.getBytes(StandardCharsets.UTF_8));
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo("malformed-message");
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");

        long taskId = insertPendingTask();
        when(handlerRegistry.get(TestTaskTypes.TYPE)).thenReturn(handler);
        when(handler.execute(org.mockito.ArgumentMatchers.any(TaskExecutionContext.class)))
                .thenReturn(TaskExecutionResult.completed("ok"));
        publish(taskId);
        awaitStatus(taskId, "COMPLETED");
    }

    @Test
    void unknownTaskIsDeadLetteredButTerminalDuplicateIsAcknowledged() throws Exception {
        publish(Long.MAX_VALUE);
        assertThat(awaitDeadLetter()).isNotNull();

        long taskId = insertTerminalTask();
        publish(taskId);
        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(rabbitTemplate.receive(messagingProperties.deadLetterQueue())).isNull());
        verify(handlerRegistry, org.mockito.Mockito.never()).get(org.mockito.ArgumentMatchers.any());
    }

    private long insertPendingTask() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tasks (organization_id, resource_id, type, status, created_by, created_at)
                VALUES (?, ?, 'TEST_OPERATION', 'PENDING', ?, now()) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID);
    }

    private long insertTerminalTask() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tasks
                    (organization_id, resource_id, type, status, created_by, created_at, started_at, completed_at)
                VALUES (?, ?, 'TEST_OPERATION', 'COMPLETED', ?, now(), now(), now()) RETURNING id
                """, Long.class, organizationId, resourceId, TestAuthentication.USER_ID);
    }

    private void publish(long taskId) throws Exception {
        assertThat(commandPublisher.publish(UUID.randomUUID(), new TaskExecutionCommand(taskId))).isTrue();
    }

    private void awaitStatus(long taskId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tasks WHERE id = ?", String.class, taskId
        )).isEqualTo(expectedStatus));
    }

    private Message awaitDeadLetter() {
        AtomicReference<Message> message = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            message.set(rabbitTemplate.receive(messagingProperties.deadLetterQueue()));
            return message.get() != null;
        });
        return message.get();
    }
}
