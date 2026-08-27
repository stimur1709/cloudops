package com.github.stimur1709.cloudops.task.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskMessagingProperties.class)
public class TaskMessagingConfiguration {

    @Bean
    DirectExchange taskExchange(TaskMessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue taskExecutionQueue(TaskMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    Binding taskExecutionBinding(
            @Qualifier("taskExecutionQueue") Queue taskExecutionQueue,
            @Qualifier("taskExchange") DirectExchange taskExchange,
            TaskMessagingProperties properties
    ) {
        return BindingBuilder.bind(taskExecutionQueue).to(taskExchange).with(properties.routingKey());
    }

    @Bean
    DirectExchange taskDeadLetterExchange(TaskMessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue taskDeadLetterQueue(TaskMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    Binding taskDeadLetterBinding(
            @Qualifier("taskDeadLetterQueue") Queue taskDeadLetterQueue,
            @Qualifier("taskDeadLetterExchange") DirectExchange taskDeadLetterExchange,
            TaskMessagingProperties properties
    ) {
        return BindingBuilder.bind(taskDeadLetterQueue)
                .to(taskDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
