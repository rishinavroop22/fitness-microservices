package com.enterprise.knowledge.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DocumentCatalogTools {

    private final RestClient documentServiceClient;

    public DocumentCatalogTools(RestClient documentServiceClient) {
        this.documentServiceClient = documentServiceClient;
    }

    @Tool(name = "search_document_catalog",
            description = "Search the enterprise document library by title, file name or tag. Returns document "
                    + "metadata (id, title, department, tags, uploader, indexing status, upload date), not content.")
    public String searchDocumentCatalog(
            @ToolParam(description = "Keywords to match against titles, file names and tags") String query,
            @ToolParam(description = "Department to scope the search to, e.g. HR or ENGINEERING; "
                    + "use ALL for company-wide documents", required = false) String department) {
        return BackendCall.run("search_document_catalog", () -> documentServiceClient.get()
                .uri(b -> b.path("/api/documents").queryParam("q", query == null ? "" : query)
                        .queryParam("department", department == null || department.isBlank() ? "ALL" : department)
                        .queryParam("limit", 20).build())
                .retrieve().body(String.class));
    }

    @Tool(name = "get_document_details",
            description = "Get metadata for one document by id: title, file type, size, department, tags, "
                    + "uploader, chunk count and indexing status.")
    public String getDocumentDetails(
            @ToolParam(description = "Document id (UUID)") String documentId,
            @ToolParam(description = "Department of the caller", required = false) String department) {
        return BackendCall.run("get_document_details", () -> documentServiceClient.get()
                .uri(b -> b.path("/api/documents/{id}")
                        .queryParam("department", department == null || department.isBlank() ? "ALL" : department)
                        .build(documentId))
                .retrieve().body(String.class));
    }

    @Tool(name = "get_knowledge_base_stats",
            description = "Count documents in the knowledge base by indexing status (PROCESSING, INDEXED, FAILED).")
    public String getKnowledgeBaseStats() {
        return BackendCall.run("get_knowledge_base_stats", () -> documentServiceClient.get()
                .uri("/api/documents/stats").retrieve().body(String.class));
    }
}
