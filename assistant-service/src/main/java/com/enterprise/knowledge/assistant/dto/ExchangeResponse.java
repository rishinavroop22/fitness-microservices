package com.enterprise.knowledge.assistant.dto;

/** One question/answer round trip. */
public record ExchangeResponse(ConversationResponse conversation, MessageResponse question, MessageResponse answer) {
}
