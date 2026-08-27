package com.github.stimur1709.cloudops.task.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskMessagingProperties.class)
public class TaskMessagingConfiguration {

    @Bean
    DirectExchange taskExchange(TaskMessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue taskExecutionQueue(TaskMessagingProperties properties) {
        return new Queue(properties.queue(), true);
    }

    @Bean
    Binding taskExecutionBinding(
            Queue taskExecutionQueue,
            DirectExchange taskExchange,
            TaskMessagingProperties properties
    ) {
        return BindingBuilder.bind(taskExecutionQueue).to(taskExchange).with(properties.routingKey());
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
