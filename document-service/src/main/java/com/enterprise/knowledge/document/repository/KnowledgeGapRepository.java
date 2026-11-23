package com.enterprise.knowledge.document.repository;

import com.enterprise.knowledge.document.model.GapStatus;
import com.enterprise.knowledge.document.model.KnowledgeGap;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeGapRepository extends JpaRepository<KnowledgeGap, UUID> {

    List<KnowledgeGap> findByStatusOrderByCreatedAtDesc(GapStatus status, Pageable pageable);

    List<KnowledgeGap> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
