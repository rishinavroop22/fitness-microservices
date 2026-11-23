package com.enterprise.knowledge.document.messaging;

/** Sent back by the AI agent service once indexing has finished or failed. */
public record DocumentStatusEvent(String documentId, String status, Integer chunkCount, String error) {
}
