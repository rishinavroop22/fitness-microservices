package com.enterprise.knowledge.document.dto;

import com.enterprise.knowledge.document.model.KnowledgeDocument;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentResponse(
        String id,
        String title,
        String fileName,
        String contentType,
        long sizeBytes,
        int characterCount,
        String department,
        List<String> tags,
        String ownerId,
        String status,
        int chunkCount,
        String errorMessage,
        LocalDateTime indexedAt,
        LocalDateTime createdAt) {

    public static DocumentResponse from(KnowledgeDocument d) {
        return new DocumentResponse(String.valueOf(d.getId()), d.getTitle(), d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getCharacterCount(), d.getDepartment(), List.copyOf(d.getTags()), d.getOwnerId(),
                d.getStatus().name(), d.getChunkCount(), d.getErrorMessage(), d.getIndexedAt(), d.getCreatedAt());
    }
}
