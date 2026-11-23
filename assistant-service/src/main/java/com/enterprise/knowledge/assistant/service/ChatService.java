package com.enterprise.knowledge.assistant.service;

import com.enterprise.knowledge.assistant.agent.AgentClient;
import com.enterprise.knowledge.assistant.agent.AgentProperties;
import com.enterprise.knowledge.assistant.agent.AgentRequest;
import com.enterprise.knowledge.assistant.agent.AgentResponse;
import com.enterprise.knowledge.assistant.dto.ConversationResponse;
import com.enterprise.knowledge.assistant.dto.ExchangeResponse;
import com.enterprise.knowledge.assistant.dto.MessageResponse;
import com.enterprise.knowledge.assistant.model.ChatMessage;
import com.enterprise.knowledge.assistant.model.Conversation;
import com.enterprise.knowledge.assistant.model.MessageRole;
import com.enterprise.knowledge.assistant.repository.ChatMessageRepository;
import com.enterprise.knowledge.assistant.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_LENGTH = 60;
    private static final String DEFAULT_TITLE = "New conversation";

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final AgentClient agentClient;
    private final AgentProperties agentProperties;
    private final TransactionTemplate transactions;

    @Transactional
    public ConversationResponse createConversation(String userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        if (title != null && !title.isBlank()) {
            conversation.setTitle(truncate(title.trim()));
        }
        return ConversationResponse.from(conversations.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(String userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ConversationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(String conversationId, String userId) {
        return messages.findByConversationOrderByCreatedAtAsc(owned(conversationId, userId)).stream()
                .map(MessageResponse::from).toList();
    }

    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        Conversation conversation = owned(conversationId, userId);
        messages.deleteByConversation(conversation);
        conversations.delete(conversation);
    }

    /**
     * Sends the question, the user's department (for document-level access control) and recent
     * history to the LangGraph agent, then stores both sides of the exchange. The agent call runs
     * outside any transaction so a slow LLM response never holds a database connection.
     */
    public ExchangeResponse ask(String conversationId, String question, String userId, String department) {
        String text = question.trim();
        PreparedTurn turn = transactions.execute(status -> {
            Conversation conversation = conversationId == null || conversationId.isBlank()
                    ? conversations.save(newConversation(userId, text))
                    : owned(conversationId, userId);
            List<AgentRequest.Turn> history = recentHistory(conversation);
            if (history.isEmpty() && DEFAULT_TITLE.equals(conversation.getTitle())) {
                conversation.setTitle(truncate(text));
            }
            ChatMessage userMessage = new ChatMessage();
            userMessage.setConversation(conversation);
            userMessage.setRole(MessageRole.USER);
            userMessage.setContent(text);
            return new PreparedTurn(conversation.getId(), MessageResponse.from(messages.save(userMessage)), history);
        });

        long started = System.currentTimeMillis();
        AgentResponse reply = agentClient.ask(
                new AgentRequest(text, userId, department, turn.conversationId().toString(), turn.history()));
        long latency = System.currentTimeMillis() - started;

        return transactions.execute(status -> {
            Conversation conversation = conversations.findById(turn.conversationId())
                    .orElseThrow(() -> new ConversationNotFoundException(turn.conversationId().toString()));
            ChatMessage answer = new ChatMessage();
            answer.setConversation(conversation);
            answer.setRole(MessageRole.ASSISTANT);
            answer.setContent(reply.answer());
            answer.setIntent(reply.intent());
            answer.setGapReported(reply.gapReported());
            answer.setLatencyMs(latency);
            answer.setCitations(nonNull(reply.citations()));
            answer.setToolCalls(nonNull(reply.toolCalls()));
            answer.setSteps(nonNull(reply.steps()));
            answer = messages.save(answer);
            conversation.setUpdatedAt(LocalDateTime.now());
            return new ExchangeResponse(ConversationResponse.from(conversation), turn.question(),
                    MessageResponse.from(answer));
        });
    }

    private record PreparedTurn(UUID conversationId, MessageResponse question, List<AgentRequest.Turn> history) {
    }

    private List<AgentRequest.Turn> recentHistory(Conversation conversation) {
        List<ChatMessage> recent = new ArrayList<>(messages.findByConversationOrderByCreatedAtDesc(
                conversation, PageRequest.of(0, agentProperties.historyTurns())));
        Collections.reverse(recent);
        return recent.stream()
                .map(m -> new AgentRequest.Turn(m.getRole() == MessageRole.USER ? "user" : "assistant", m.getContent()))
                .toList();
    }

    private Conversation owned(String conversationId, String userId) {
        UUID id;
        try {
            id = UUID.fromString(conversationId);
        } catch (IllegalArgumentException e) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversations.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    private Conversation newConversation(String userId, String question) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(truncate(question.trim()));
        return conversation;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private static String truncate(String text) {
        return text.length() <= TITLE_LENGTH ? text : text.substring(0, TITLE_LENGTH - 1) + "…";
    }
}
