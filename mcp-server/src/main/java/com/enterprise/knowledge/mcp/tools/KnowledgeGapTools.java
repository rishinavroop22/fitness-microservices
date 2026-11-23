package com.enterprise.knowledge.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KnowledgeGapTools {

    private final RestClient documentServiceClient;

    public KnowledgeGapTools(RestClient documentServiceClient) {
        this.documentServiceClient = documentServiceClient;
    }

    @Tool(name = "report_knowledge_gap",
            description = "Record a question the knowledge base could not answer so document owners can fill the gap.")
    public String reportKnowledgeGap(
            @ToolParam(description = "The question that could not be answered") String question,
            @ToolParam(description = "Id of the user who asked", required = false) String userId,
            @ToolParam(description = "Why it could not be answered", required = false) String reason) {
        return BackendCall.run("report_knowledge_gap", () -> documentServiceClient.post()
                .uri("/api/knowledge-gaps")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", question,
                        "userId", userId == null ? "" : userId,
                        "reason", reason == null ? "" : reason))
                .retrieve().body(String.class));
    }

    @Tool(name = "list_open_knowledge_gaps",
            description = "List recent unanswered questions that are still open.")
    public String listOpenKnowledgeGaps(
            @ToolParam(description = "Maximum number of gaps to return (1-50)", required = false) Integer limit) {
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        return BackendCall.run("list_open_knowledge_gaps", () -> documentServiceClient.get()
                .uri(b -> b.path("/api/knowledge-gaps").queryParam("status", "OPEN").queryParam("limit", size).build())
                .retrieve().body(String.class));
    }
}
