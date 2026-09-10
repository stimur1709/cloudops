package com.github.stimur1709.cloudops.task.outbox;

import com.github.stimur1709.cloudops.task.messaging.TaskExecutionCommand;
import java.util.UUID;

public interface TaskExecutionCommandPublisher {

    boolean publish(UUID outboxMessageId, TaskExecutionCommand command) throws Exception;
}
