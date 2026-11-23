package com.enterprise.knowledge.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Model Context Protocol server. It exposes the enterprise Spring Boot services as standard MCP tools
 * (SSE transport at {@code /sse}) so any MCP-capable AI agent — here the LangGraph agent — can discover
 * and call them without service-specific client code.
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
