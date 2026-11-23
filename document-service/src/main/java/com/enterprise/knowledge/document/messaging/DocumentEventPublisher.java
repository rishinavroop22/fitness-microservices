package com.enterprise.knowledge.document.messaging;

import com.enterprise.knowledge.document.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DocumentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final KnowledgeProperties.Messaging messaging;

    public DocumentEventPublisher(RabbitTemplate rabbitTemplate, KnowledgeProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.messaging = properties.messaging();
    }

    public void publishIngestion(DocumentIngestionEvent event) {
        rabbitTemplate.convertAndSend(messaging.exchange(), messaging.ingestRoutingKey(), event);
        log.info("Queued document {} ({} chars) for indexing", event.documentId(), event.content().length());
    }

    public void publishDeletion(DocumentDeletedEvent event) {
        rabbitTemplate.convertAndSend(messaging.exchange(), messaging.deleteRoutingKey(), event);
    }
}
