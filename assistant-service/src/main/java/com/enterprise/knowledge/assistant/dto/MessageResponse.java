package com.enterprise.knowledge.assistant.dto;

import com.enterprise.knowledge.assistant.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MessageResponse(String id, String conversationId, String role, String content, String intent,
                              boolean gapReported, long latencyMs, List<Map<String, Object>> citations,
                              List<Map<String, Object>> toolCalls, List<Map<String, Object>> steps,
                              LocalDateTime createdAt) {

    public static MessageResponse from(ChatMessage m) {
        return new MessageResponse(String.valueOf(m.getId()), String.valueOf(m.getConversation().getId()),
                m.getRole().name(), m.getContent(), m.getIntent(), m.isGapReported(), m.getLatencyMs(),
                m.getCitations(), m.getToolCalls(), m.getSteps(), m.getCreatedAt());
    }
}
