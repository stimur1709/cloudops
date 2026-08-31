package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.config.TaskLeaseProperties;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageEntity;
import com.github.stimur1709.cloudops.task.outbox.persistence.OutboxMessageJpaRepository;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import com.github.stimur1709.cloudops.task.persistence.TaskJpaRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);
    private static final String RECOVERY_EXHAUSTED_MESSAGE = "Task execution could not be recovered";

    private final TaskJpaRepository taskRepository;
    private final OutboxMessageJpaRepository outboxRepository;
    private final TaskLeaseProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public TaskRecoveryService(
            TaskJpaRepository taskRepository,
            OutboxMessageJpaRepository outboxRepository,
            TaskLeaseProperties properties,
            Clock clock,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int recoverExpired() {
        var tasks = taskRepository.lockExpired(clock.instant(), PageRequest.of(0, properties.recoveryBatchSize()));
        for (TaskEntity task : tasks) {
            recover(task);
        }
        taskRepository.flush();
        outboxRepository.flush();
        return tasks.size();
    }

    private void recover(TaskEntity task) {
        if (task.recoveryCount() >= properties.maxRecoveries()) {
            task.failRecovery(RECOVERY_EXHAUSTED_MESSAGE, clock.instant());
            log.warn("event=task_recovery_exhausted taskId={} recoveryCount={}", task.id(), task.recoveryCount());
            return;
        }

        task.recover();
        var payload = objectMapper.createObjectNode().put("taskId", task.id());
        OutboxMessageEntity message =
                OutboxMessageEntity.taskExecutionRequested(task.id(), task.recoveryCount(), payload, clock.instant());
        outboxRepository.save(message);
        log.info("event=expired_task_recovered taskId={} recoveryCount={}", task.id(), task.recoveryCount());
        log.info(
                "event=recovery_outbox_message_created taskId={} messageId={} deduplicationKey={}",
                task.id(),
                message.id(),
                message.deduplicationKey());
    }
}
