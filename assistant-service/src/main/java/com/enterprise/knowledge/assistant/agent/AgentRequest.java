package com.enterprise.knowledge.assistant.agent;

import java.util.List;

public record AgentRequest(String question, String userId, String department, String conversationId,
                           List<Turn> history) {

    public record Turn(String role, String content) {
    }
}
