package com.github.stimur1709.cloudops.task.outbox;

import java.time.Clock;
import java.util.UUID;

import com.github.stimur1709.cloudops.task.messaging.TaskExecutionCommand;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageEntity;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessageProcessor.class);

    private final OutboxMessageJpaRepository repository;
    private final TaskExecutionCommandPublisher publisher;
    private final Clock clock;

    OutboxMessageProcessor(
            OutboxMessageJpaRepository repository,
            TaskExecutionCommandPublisher publisher,
            Clock clock
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    OutboxProcessingResult process(UUID messageId) {
        OutboxMessageEntity message = repository.lockUnpublished(messageId).orElse(null);
        if (message == null) {
            return OutboxProcessingResult.SKIPPED;
        }

        try {
            boolean confirmed = publisher.publish(
                    message.id(), new TaskExecutionCommand(message.payload().required("taskId").asLong())
            );
            if (!confirmed) {
                log.warn("Outbox publication was not confirmed: messageId={}, messageType={}, aggregateId={}",
                        message.id(), message.messageType(), message.aggregateId());
                return OutboxProcessingResult.FAILED;
            }
            message.markPublished(clock.instant());
            repository.flush();
            log.info("Published outbox message: messageId={}, messageType={}, aggregateId={}",
                    message.id(), message.messageType(), message.aggregateId());
            return OutboxProcessingResult.PUBLISHED;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to publish outbox message: messageId={}, messageType={}, aggregateId={}, reason={}",
                    message.id(), message.messageType(), message.aggregateId(), exception.getClass().getSimpleName());
            return OutboxProcessingResult.FAILED;
        }
    }
}
