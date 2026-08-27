package com.github.stimur1709.cloudops.task.messaging;

import com.github.stimur1709.cloudops.task.application.TaskExecutionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutionListener {

    private final TaskExecutionService executionService;

    public TaskExecutionListener(TaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(queues = "${cloudops.task.messaging.queue}")
    public void receive(TaskExecutionCommand command) {
        executionService.execute(command.taskId());
    }
}
