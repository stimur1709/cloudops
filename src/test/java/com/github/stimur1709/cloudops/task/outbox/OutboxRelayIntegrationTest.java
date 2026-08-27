package com.github.stimur1709.cloudops.task.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.task.messaging.TaskExecutionCommand;
import com.github.stimur1709.cloudops.task.messaging.TaskMessagingProperties;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cloudops.task.outbox.enabled=false")
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxMessageJpaRepository repository;

    @Autowired
    private OutboxMessageProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TaskMessagingProperties messagingProperties;

    @MockitoBean
    private TaskExecutionCommandPublisher publisher;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_messages");
        reset(publisher);
        when(publisher.publish(any(), any())).thenReturn(true);
        relay = new OutboxRelay(repository, processor, new OutboxRelayProperties(false, Duration.ofSeconds(1), 2));
    }

    @Test
    void publishesStableLimitedBatchAndMarksOnlyConfirmedMessages() throws Exception {
        UUID first = insertMessage(101, Instant.parse("2026-08-27T00:00:01Z"));
        UUID second = insertMessage(102, Instant.parse("2026-08-27T00:00:02Z"));
        UUID third = insertMessage(103, Instant.parse("2026-08-27T00:00:03Z"));

        relay.publishPending();

        ArgumentCaptor<TaskExecutionCommand> commands = ArgumentCaptor.forClass(TaskExecutionCommand.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(any(), commands.capture());
        assertThat(commands.getAllValues()).extracting(TaskExecutionCommand::taskId).containsExactly(101L, 102L);
        assertThat(publishedAt(first)).isNotNull();
        assertThat(publishedAt(second)).isNotNull();
        assertThat(publishedAt(third)).isNull();
    }

    @Test
    void nackAndExceptionLeaveMessagesUnpublishedWithoutBlockingNextMessage() throws Exception {
        UUID nacked = insertMessage(201, Instant.parse("2026-08-27T00:00:01Z"));
        UUID failed = insertMessage(202, Instant.parse("2026-08-27T00:00:02Z"));
        when(publisher.publish(any(), any()))
                .thenReturn(false)
                .thenThrow(new IllegalStateException("broker unavailable"));

        relay.publishPending();

        assertThat(publishedAt(nacked)).isNull();
        assertThat(publishedAt(failed)).isNull();
        verify(publisher, org.mockito.Mockito.times(2)).publish(any(), any());
    }

    @Test
    void concurrentRelaysDoNotPublishTheSameRowAtTheSameTime() throws Exception {
        insertMessage(301, Instant.parse("2026-08-27T00:00:01Z"));
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            publishing.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        }).when(publisher).publish(any(), any());
        OutboxRelay secondRelay = new OutboxRelay(
                repository, processor, new OutboxRelayProperties(false, Duration.ofSeconds(1), 2)
        );

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstRun = executor.submit(relay::publishPending);
            assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
            var secondRun = executor.submit(secondRelay::publishPending);
            secondRun.get(5, TimeUnit.SECONDS);
            release.countDown();
            firstRun.get(5, TimeUnit.SECONDS);
        }

        verify(publisher, org.mockito.Mockito.times(1)).publish(any(), any());
    }

    @Test
    void unroutableRabbitMessageIsNotConsideredPublished() throws Exception {
        var unroutableProperties = new TaskMessagingProperties(
                messagingProperties.exchange(), messagingProperties.queue(), "missing." + UUID.randomUUID(),
                messagingProperties.deadLetterExchange(), messagingProperties.deadLetterQueue(),
                messagingProperties.deadLetterRoutingKey()
        );
        var rabbitPublisher = new RabbitOutboxMessagePublisher(rabbitTemplate, unroutableProperties);

        assertThat(rabbitPublisher.publish(UUID.randomUUID(), new TaskExecutionCommand(401))).isFalse();
    }

    private UUID insertMessage(long taskId, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_messages
                    (id, message_type, aggregate_type, aggregate_id, payload, created_at, deduplication_key)
                VALUES (?, 'TASK_EXECUTION_REQUESTED', 'TASK', ?, CAST(? AS jsonb), ?, ?)
                """, id, taskId, "{\"taskId\":" + taskId + "}", java.sql.Timestamp.from(createdAt),
                "test:" + id);
        return id;
    }

    private Instant publishedAt(UUID id) {
        List<Instant> values = jdbcTemplate.query(
                "SELECT published_at FROM outbox_messages WHERE id = ?",
                (resultSet, row) -> resultSet.getObject(1, java.time.OffsetDateTime.class) == null
                        ? null
                        : resultSet.getObject(1, java.time.OffsetDateTime.class).toInstant(),
                id
        );
        return values.getFirst();
    }
}
