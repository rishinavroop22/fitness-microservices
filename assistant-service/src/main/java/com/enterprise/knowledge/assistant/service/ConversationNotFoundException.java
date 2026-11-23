package com.enterprise.knowledge.assistant.service;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(String id) {
        super("Conversation not found: " + id);
    }
}
