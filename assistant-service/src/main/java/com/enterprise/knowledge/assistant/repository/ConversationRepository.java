package com.enterprise.knowledge.assistant.repository;

import com.enterprise.knowledge.assistant.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<Conversation> findByIdAndUserId(UUID id, String userId);
}
