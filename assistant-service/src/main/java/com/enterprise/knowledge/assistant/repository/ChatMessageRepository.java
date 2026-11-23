package com.enterprise.knowledge.assistant.repository;

import com.enterprise.knowledge.assistant.model.ChatMessage;
import com.enterprise.knowledge.assistant.model.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    List<ChatMessage> findByConversationOrderByCreatedAtDesc(Conversation conversation, Pageable pageable);

    void deleteByConversation(Conversation conversation);
}
