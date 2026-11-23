package com.enterprise.knowledge.mcp;

import com.enterprise.knowledge.mcp.config.McpToolsConfig;
import com.enterprise.knowledge.mcp.tools.DocumentCatalogTools;
import com.enterprise.knowledge.mcp.tools.KnowledgeGapTools;
import com.enterprise.knowledge.mcp.tools.UserDirectoryTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.client.RestClient;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistrationTests {

    @Test
    void allEnterpriseToolsArePublishedWithSnakeCaseNames() {
        RestClient client = RestClient.create("http://localhost:1");
        ToolCallbackProvider provider = new McpToolsConfig().enterpriseTools(
                new DocumentCatalogTools(client), new UserDirectoryTools(client), new KnowledgeGapTools(client));

        assertThat(Arrays.stream(provider.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("search_document_catalog", "get_document_details",
                        "get_knowledge_base_stats", "get_user_profile", "find_users_by_department",
                        "report_knowledge_gap", "list_open_knowledge_gaps");
    }

    @Test
    void unreachableBackendReturnsReadableErrorInsteadOfThrowing() {
        DocumentCatalogTools tools = new DocumentCatalogTools(RestClient.create("http://localhost:1"));
        assertThat(tools.searchDocumentCatalog("leave", "HR")).contains("temporarily unavailable");
    }

    @Test
    void reportKnowledgeGapSchemaRequiresTheQuestion() {
        ToolCallback callback = Arrays.stream(new McpToolsConfig().enterpriseTools(
                        new DocumentCatalogTools(RestClient.create("http://x")),
                        new UserDirectoryTools(RestClient.create("http://x")),
                        new KnowledgeGapTools(RestClient.create("http://x"))).getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals("report_knowledge_gap"))
                .findFirst().orElseThrow();
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"question\"");
    }
}
