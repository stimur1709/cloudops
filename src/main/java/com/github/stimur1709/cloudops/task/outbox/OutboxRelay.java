package com.github.stimur1709.cloudops.task.outbox;

import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageJpaRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cloudops.task.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageJpaRepository repository;
    private final OutboxMessageProcessor processor;
    private final OutboxRelayProperties properties;
    private final Set<UUID> previouslyFailed = ConcurrentHashMap.newKeySet();

    OutboxRelay(
            OutboxMessageJpaRepository repository, OutboxMessageProcessor processor, OutboxRelayProperties properties) {
        this.repository = repository;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cloudops.task.outbox.poll-interval:500ms}")
    void publishPending() {
        var ids = repository.findUnpublishedIds(PageRequest.of(0, properties.batchSize()));
        for (UUID id : ids) {
            if (previouslyFailed.contains(id)) {
                log.info("Retrying outbox publication: messageId={}", id);
            }
            OutboxProcessingResult result = processor.process(id);
            if (result == OutboxProcessingResult.FAILED) {
                previouslyFailed.add(id);
            } else if (result == OutboxProcessingResult.PUBLISHED) {
                previouslyFailed.remove(id);
            }
        }
    }
}
