package com.github.stimur1709.cloudops.task.outbox;

import com.github.stimur1709.cloudops.task.messaging.TaskExecutionCommand;
import com.github.stimur1709.cloudops.task.messaging.TaskMessagingProperties;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitOutboxMessagePublisher implements TaskExecutionCommandPublisher {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final TaskMessagingProperties properties;

    public RabbitOutboxMessagePublisher(RabbitTemplate rabbitTemplate, TaskMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public boolean publish(UUID outboxMessageId, TaskExecutionCommand command) throws Exception {
        CorrelationData correlation = new CorrelationData(outboxMessageId.toString());
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                command,
                message -> {
                    message.getMessageProperties().setMessageId(outboxMessageId.toString());
                    return message;
                },
                correlation);

        CorrelationData.Confirm confirm =
                correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        return confirm.ack() && correlation.getReturned() == null;
    }
}
