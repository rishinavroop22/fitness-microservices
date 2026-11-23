package com.enterprise.knowledge.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge")
public record KnowledgeProperties(Storage storage, Extraction extraction, Messaging messaging) {

    public record Storage(String path) {
    }

    public record Extraction(int maxCharacters) {
    }

    public record Messaging(String exchange, String ingestQueue, String deleteQueue, String statusQueue,
                            String ingestRoutingKey, String deleteRoutingKey, String statusRoutingKey) {
    }
}
