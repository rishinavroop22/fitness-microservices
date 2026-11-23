package com.enterprise.knowledge.assistant.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/** HTTP client for the Python LangGraph agent (ai-agent-service). */
@Component
@Slf4j
public class AgentClient {

    private final WebClient webClient;
    private final AgentProperties properties;

    public AgentClient(WebClient.Builder builder, AgentProperties properties) {
        this.properties = properties;
        this.webClient = builder.baseUrl(properties.baseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    public AgentResponse ask(AgentRequest request) {
        try {
            AgentResponse response = webClient.post()
                    .uri("/agent/invoke")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AgentResponse.class)
                    .block(properties.timeout());
            if (response == null) {
                throw new IllegalStateException("Empty response from agent");
            }
            return response;
        } catch (RuntimeException e) {
            log.error("AI agent call failed: {}", e.getMessage());
            return new AgentResponse(
                    "The knowledge assistant is temporarily unavailable. Please try again in a moment.",
                    "error", request.question(), List.of(), List.of(), false,
                    List.of(Map.of("node", "agent_unavailable", "error", String.valueOf(e.getMessage()))));
        }
    }
}
