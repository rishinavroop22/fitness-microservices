package com.enterprise.knowledge.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 4000) String question, String conversationId) {
}
