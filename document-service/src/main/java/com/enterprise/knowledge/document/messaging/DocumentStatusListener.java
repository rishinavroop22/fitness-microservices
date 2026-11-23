package com.enterprise.knowledge.document.messaging;

import com.enterprise.knowledge.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentStatusListener {

    private final DocumentService documentService;

    @RabbitListener(queues = "${knowledge.messaging.status-queue}")
    public void onStatus(DocumentStatusEvent event) {
        log.info("Document {} -> {} ({} chunks)", event.documentId(), event.status(), event.chunkCount());
        documentService.applyIndexingResult(event);
    }
}
