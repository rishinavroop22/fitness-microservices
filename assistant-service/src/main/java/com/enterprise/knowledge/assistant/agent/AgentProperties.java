package com.enterprise.knowledge.assistant.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(String baseUrl, Duration timeout, int historyTurns) {

    public AgentProperties {
        if (baseUrl == null) baseUrl = "http://localhost:8000";
        if (timeout == null) timeout = Duration.ofSeconds(90);
        if (historyTurns <= 0) historyTurns = 6;
    }
}
