package com.enterprise.knowledge.assistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_messages_conversation", columnList = "conversation_id"))
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** Agent intent (knowledge / catalog / smalltalk); assistant messages only. */
    private String intent;

    private boolean gapReported;

    private long latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> citations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> toolCalls = new ArrayList<>();

    /** LangGraph node trace, shown in the UI so users can see how an answer was produced. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> steps = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
