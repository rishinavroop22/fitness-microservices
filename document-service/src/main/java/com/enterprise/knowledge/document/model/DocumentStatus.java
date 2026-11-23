package com.enterprise.knowledge.document.model;

public enum DocumentStatus {
    /** Text extracted, waiting for the AI service to chunk and embed it. */
    PROCESSING,
    /** Embedded in ChromaDB and available to the assistant. */
    INDEXED,
    FAILED
}
