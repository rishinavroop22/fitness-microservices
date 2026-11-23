package com.enterprise.knowledge.assistant.dto;

import com.enterprise.knowledge.assistant.model.Conversation;

import java.time.LocalDateTime;

public record ConversationResponse(String id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(String.valueOf(c.getId()), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
