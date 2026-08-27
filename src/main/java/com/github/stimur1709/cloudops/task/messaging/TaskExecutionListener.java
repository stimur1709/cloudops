package com.github.stimur1709.cloudops.task.messaging;

import com.github.stimur1709.cloudops.task.execution.TaskExecutionService;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionListener.class);

    private final TaskExecutionService executionService;

    public TaskExecutionListener(TaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(queues = "${cloudops.task.messaging.queue}")
    public void receive(TaskExecutionCommand command) {
        TaskExecutionOutcome outcome = executionService.execute(command.taskId());
        if (outcome == TaskExecutionOutcome.DEAD_LETTER) {
            LOGGER.error("event=task_message_dead_lettered taskId={}", command.taskId());
            throw new AmqpRejectAndDontRequeueException("Task execution command was rejected");
        }
    }
}
