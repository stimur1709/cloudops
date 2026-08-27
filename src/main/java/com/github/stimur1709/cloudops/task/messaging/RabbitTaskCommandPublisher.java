package com.github.stimur1709.cloudops.task.messaging;

import com.github.stimur1709.cloudops.task.application.TaskCommandPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitTaskCommandPublisher implements TaskCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TaskMessagingProperties properties;

    public RabbitTaskCommandPublisher(RabbitTemplate rabbitTemplate, TaskMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(long taskId) {
        rabbitTemplate.convertAndSend(
                properties.exchange(), properties.routingKey(), new TaskExecutionCommand(taskId)
        );
    }
}
