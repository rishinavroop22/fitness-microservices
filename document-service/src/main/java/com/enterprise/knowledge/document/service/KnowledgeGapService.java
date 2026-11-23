package com.enterprise.knowledge.document.service;

import com.enterprise.knowledge.document.dto.KnowledgeGapRequest;
import com.enterprise.knowledge.document.dto.KnowledgeGapResponse;
import com.enterprise.knowledge.document.model.GapStatus;
import com.enterprise.knowledge.document.model.KnowledgeGap;
import com.enterprise.knowledge.document.repository.KnowledgeGapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeGapService {

    private final KnowledgeGapRepository repository;

    @Transactional
    public KnowledgeGapResponse report(KnowledgeGapRequest request) {
        KnowledgeGap gap = new KnowledgeGap();
        gap.setQuestion(request.question().trim());
        gap.setUserId(request.userId());
        gap.setReason(request.reason());
        return KnowledgeGapResponse.from(repository.save(gap));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeGapResponse> list(GapStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<KnowledgeGap> gaps = status == null
                ? repository.findAllByOrderByCreatedAtDesc(page)
                : repository.findByStatusOrderByCreatedAtDesc(status, page);
        return gaps.stream().map(KnowledgeGapResponse::from).toList();
    }

    @Transactional
    public KnowledgeGapResponse resolve(UUID id, String note) {
        KnowledgeGap gap = repository.findById(id).orElseThrow(() -> new NotFoundException("Gap not found: " + id));
        gap.setStatus(GapStatus.RESOLVED);
        gap.setResolutionNote(note);
        gap.setResolvedAt(LocalDateTime.now());
        return KnowledgeGapResponse.from(gap);
    }
}
