package com.enterprise.knowledge.document.controller;

import com.enterprise.knowledge.document.dto.DocumentResponse;
import com.enterprise.knowledge.document.model.DocumentStatus;
import com.enterprise.knowledge.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identity headers are set by the API gateway from the verified Keycloak token. Internal callers
 * (the MCP server) pass {@code department} explicitly.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String USER_ID = "X-User-ID";
    private static final String DEPARTMENT = "X-User-Department";

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> tags,
            @RequestHeader(USER_ID) String userId,
            @RequestHeader(value = DEPARTMENT, required = false) String userDepartment) throws IOException {
        // Users may share with the whole company (ALL) or with their own department only.
        String target = "ALL".equalsIgnoreCase(department) ? "ALL" : userDepartment;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(documentService.upload(file, title, target, tags, userId));
    }

    @GetMapping
    public List<DocumentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String department,
            @RequestHeader(value = DEPARTMENT, required = false) String userDepartment) {
        return documentService.search(q, userDepartment != null ? userDepartment : department, status, limit);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return documentService.stats();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id,
                                @RequestParam(required = false) String department,
                                @RequestHeader(value = DEPARTMENT, required = false) String userDepartment) {
        return documentService.get(id, userDepartment != null ? userDepartment : department);
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<DocumentResponse> reindex(@PathVariable UUID id,
                                                    @RequestHeader(value = DEPARTMENT, required = false) String dept)
            throws IOException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.reindex(id, dept));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader(USER_ID) String userId,
                                       @RequestHeader(value = DEPARTMENT, required = false) String dept) {
        documentService.delete(id, userId, dept);
        return ResponseEntity.noContent().build();
    }
}
