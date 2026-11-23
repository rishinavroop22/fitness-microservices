package com.enterprise.knowledge.assistant.controller;

import com.enterprise.knowledge.assistant.dto.AskRequest;
import com.enterprise.knowledge.assistant.dto.ConversationResponse;
import com.enterprise.knowledge.assistant.dto.ExchangeResponse;
import com.enterprise.knowledge.assistant.dto.MessageResponse;
import com.enterprise.knowledge.assistant.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Identity headers are injected by the API gateway from the verified Keycloak token. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String USER_ID = "X-User-ID";
    private static final String DEPARTMENT = "X-User-Department";

    private final ChatService chatService;

    @PostMapping("/ask")
    public ExchangeResponse ask(@Valid @RequestBody AskRequest request,
                                @RequestHeader(USER_ID) String userId,
                                @RequestHeader(value = DEPARTMENT, defaultValue = "ALL") String department) {
        return chatService.ask(request.conversationId(), request.question(), userId, department);
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> create(@RequestBody(required = false) Map<String, String> body,
                                                       @RequestHeader(USER_ID) String userId) {
        String title = body == null ? null : body.get("title");
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createConversation(userId, title));
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> list(@RequestHeader(USER_ID) String userId) {
        return chatService.listConversations(userId);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> messages(@PathVariable String id, @RequestHeader(USER_ID) String userId) {
        return chatService.listMessages(id, userId);
    }

    @PostMapping("/conversations/{id}/messages")
    public ExchangeResponse send(@PathVariable String id, @Valid @RequestBody AskRequest request,
                                 @RequestHeader(USER_ID) String userId,
                                 @RequestHeader(value = DEPARTMENT, defaultValue = "ALL") String department) {
        return chatService.ask(id, request.question(), userId, department);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestHeader(USER_ID) String userId) {
        chatService.deleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }
}
