package com.enterprise.knowledge.assistant.agent;

import java.util.List;
import java.util.Map;

public record AgentResponse(String answer, String intent, String standaloneQuestion,
                            List<Map<String, Object>> citations, List<Map<String, Object>> toolCalls,
                            boolean gapReported, List<Map<String, Object>> steps) {
}
