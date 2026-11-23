package com.enterprise.knowledge.document.messaging;

import java.util.List;

/** Published to {@code document.ingest}; consumed by the Python AI agent service, which chunks and embeds it. */
public record DocumentIngestionEvent(String documentId, String title, String fileName, String department,
                                     List<String> tags, String ownerId, String content) {
}
