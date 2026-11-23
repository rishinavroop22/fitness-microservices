package com.enterprise.knowledge.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeGapRequest(@NotBlank @Size(max = 4000) String question, String userId,
                                  @Size(max = 1000) String reason) {
}
