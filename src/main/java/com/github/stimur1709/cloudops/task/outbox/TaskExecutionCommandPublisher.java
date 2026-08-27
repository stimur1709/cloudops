package com.github.stimur1709.cloudops.task.outbox;

import java.util.UUID;

import com.github.stimur1709.cloudops.task.messaging.TaskExecutionCommand;

public interface TaskExecutionCommandPublisher {

    boolean publish(UUID outboxMessageId, TaskExecutionCommand command) throws Exception;
}
