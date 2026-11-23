package com.enterprise.knowledge.document.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology of the asynchronous ingestion pipeline. The Python AI agent service declares the same
 * queues with identical arguments, so either side can start first.
 */
@Configuration
public class RabbitMqConfig {

    private final KnowledgeProperties.Messaging messaging;

    public RabbitMqConfig(KnowledgeProperties properties) {
        this.messaging = properties.messaging();
    }

    @Bean
    public TopicExchange knowledgeExchange() {
        return new TopicExchange(messaging.exchange(), true, false);
    }

    @Bean
    public Queue ingestQueue() {
        return QueueBuilder.durable(messaging.ingestQueue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", messaging.ingestQueue() + ".dlq")
                .build();
    }

    @Bean
    public Queue ingestDeadLetterQueue() {
        return QueueBuilder.durable(messaging.ingestQueue() + ".dlq").build();
    }

    @Bean
    public Queue deleteQueue() {
        return QueueBuilder.durable(messaging.deleteQueue()).build();
    }

    @Bean
    public Queue statusQueue() {
        return QueueBuilder.durable(messaging.statusQueue()).build();
    }

    @Bean
    public Binding ingestBinding() {
        return BindingBuilder.bind(ingestQueue()).to(knowledgeExchange()).with(messaging.ingestRoutingKey());
    }

    @Bean
    public Binding deleteBinding() {
        return BindingBuilder.bind(deleteQueue()).to(knowledgeExchange()).with(messaging.deleteRoutingKey());
    }

    @Bean
    public Binding statusBinding() {
        return BindingBuilder.bind(statusQueue()).to(knowledgeExchange()).with(messaging.statusRoutingKey());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
