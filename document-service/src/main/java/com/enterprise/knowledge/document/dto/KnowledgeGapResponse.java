package com.enterprise.knowledge.document.dto;

import com.enterprise.knowledge.document.model.KnowledgeGap;

import java.time.LocalDateTime;

public record KnowledgeGapResponse(String id, String question, String userId, String reason, String status,
                                   String resolutionNote, LocalDateTime createdAt, LocalDateTime resolvedAt) {

    public static KnowledgeGapResponse from(KnowledgeGap g) {
        return new KnowledgeGapResponse(String.valueOf(g.getId()), g.getQuestion(), g.getUserId(), g.getReason(),
                g.getStatus().name(), g.getResolutionNote(), g.getCreatedAt(), g.getResolvedAt());
    }
}
