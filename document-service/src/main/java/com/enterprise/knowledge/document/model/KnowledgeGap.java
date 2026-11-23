package com.enterprise.knowledge.document.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A question the assistant could not answer from the knowledge base, reported by the AI agent via MCP. */
@Entity
@Table(name = "knowledge_gaps")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeGap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 4000)
    private String question;

    private String userId;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GapStatus status = GapStatus.OPEN;

    private String resolutionNote;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
