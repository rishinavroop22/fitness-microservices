package com.enterprise.knowledge.document.controller;

import com.enterprise.knowledge.document.dto.KnowledgeGapRequest;
import com.enterprise.knowledge.document.dto.KnowledgeGapResponse;
import com.enterprise.knowledge.document.dto.ResolveGapRequest;
import com.enterprise.knowledge.document.model.GapStatus;
import com.enterprise.knowledge.document.service.KnowledgeGapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-gaps")
@RequiredArgsConstructor
public class KnowledgeGapController {

    private final KnowledgeGapService gapService;

    @PostMapping
    public ResponseEntity<KnowledgeGapResponse> report(@Valid @RequestBody KnowledgeGapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gapService.report(request));
    }

    @GetMapping
    public List<KnowledgeGapResponse> list(@RequestParam(required = false) GapStatus status,
                                           @RequestParam(defaultValue = "50") int limit) {
        return gapService.list(status, limit);
    }

    @PatchMapping("/{id}/resolve")
    public KnowledgeGapResponse resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveGapRequest body) {
        return gapService.resolve(id, body == null ? null : body.resolutionNote());
    }
}
