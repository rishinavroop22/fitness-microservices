package com.enterprise.knowledge.mcp.config;

import com.enterprise.knowledge.mcp.tools.DocumentCatalogTools;
import com.enterprise.knowledge.mcp.tools.KnowledgeGapTools;
import com.enterprise.knowledge.mcp.tools.UserDirectoryTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    /** Every {@code @Tool} method on these beans is published to MCP clients. */
    @Bean
    public ToolCallbackProvider enterpriseTools(DocumentCatalogTools documents, UserDirectoryTools users,
                                                KnowledgeGapTools gaps) {
        return MethodToolCallbackProvider.builder().toolObjects(documents, users, gaps).build();
    }
}
