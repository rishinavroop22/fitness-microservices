package com.enterprise.knowledge.mcp.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/** Converts backend failures into readable tool results instead of protocol errors, so the agent can recover. */
@Slf4j
final class BackendCall {

    private BackendCall() {
    }

    static String run(String action, Supplier<String> call) {
        try {
            String body = call.get();
            return body == null || body.isBlank() ? "[]" : body;
        } catch (RestClientResponseException e) {
            log.warn("{} failed with {}: {}", action, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                return "{\"error\": \"not found\"}";
            }
            return "{\"error\": \"" + action + " failed with status " + e.getStatusCode().value() + "\"}";
        } catch (RuntimeException e) {
            log.warn("{} failed: {}", action, e.getMessage());
            return "{\"error\": \"" + action + " is temporarily unavailable\"}";
        }
    }
}
