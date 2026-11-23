package com.enterprise.knowledge.document.service;

import com.enterprise.knowledge.document.config.KnowledgeProperties;
import com.enterprise.knowledge.document.dto.DocumentResponse;
import com.enterprise.knowledge.document.messaging.DocumentDeletedEvent;
import com.enterprise.knowledge.document.messaging.DocumentEventPublisher;
import com.enterprise.knowledge.document.messaging.DocumentIngestionEvent;
import com.enterprise.knowledge.document.messaging.DocumentStatusEvent;
import com.enterprise.knowledge.document.model.DocumentStatus;
import com.enterprise.knowledge.document.model.KnowledgeDocument;
import com.enterprise.knowledge.document.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class DocumentService {

    public static final String PUBLIC_DEPARTMENT = "ALL";

    private final DocumentRepository repository;
    private final TextExtractionService extractor;
    private final DocumentEventPublisher publisher;
    private final Path storageRoot;

    public DocumentService(DocumentRepository repository, TextExtractionService extractor,
                           DocumentEventPublisher publisher, KnowledgeProperties properties) {
        this.repository = repository;
        this.extractor = extractor;
        this.publisher = publisher;
        this.storageRoot = Path.of(properties.storage().path()).toAbsolutePath();
    }

    // ------------------------------------------------------------------ upload
    public DocumentResponse upload(MultipartFile file, String title, String department, List<String> tags,
                                   String ownerId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new DocumentProcessingException("File is empty");
        }
        byte[] bytes = file.getBytes();
        String fileName = safeFileName(file.getOriginalFilename());
        String text = extractor.extract(new ByteArrayInputStream(bytes));
        if (text.isBlank()) {
            throw new DocumentProcessingException(
                    "No text could be extracted from " + fileName + " (scanned PDFs need OCR first)");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title == null || title.isBlank() ? stripExtension(fileName) : title.trim());
        document.setFileName(fileName);
        document.setContentType(extractor.detectType(new ByteArrayInputStream(bytes), fileName));
        document.setSizeBytes(bytes.length);
        document.setCharacterCount(text.length());
        document.setDepartment(normaliseDepartment(department));
        document.setTags(normaliseTags(tags));
        document.setOwnerId(ownerId);
        document.setStatus(DocumentStatus.PROCESSING);
        document = repository.save(document);

        Path target = storageRoot.resolve(document.getId().toString()).resolve(fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        document.setStoragePath(target.toString());
        document = repository.save(document);

        publisher.publishIngestion(toEvent(document, text));
        return DocumentResponse.from(document);
    }

    // -------------------------------------------------------------- queries
    @Transactional(readOnly = true)
    public List<DocumentResponse> search(String query, String department, DocumentStatus status, int limit) {
        String q = query == null ? "" : query.trim();
        return repository.search(q, visibleDepartments(department), status, PageRequest.of(0, clamp(limit)))
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id, String department) {
        return DocumentResponse.from(findVisible(id, department));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (DocumentStatus s : DocumentStatus.values()) {
            stats.put(s.name(), repository.countByStatus(s));
        }
        return stats;
    }

    // ------------------------------------------------------------- mutations
    @Transactional
    public void delete(UUID id, String userId, String department) {
        KnowledgeDocument document = findVisible(id, department);
        if (!document.getOwnerId().equals(userId)) {
            throw new ForbiddenException("Only the uploader can delete this document");
        }
        repository.delete(document);
        publisher.publishDeletion(new DocumentDeletedEvent(id.toString()));
        try {
            if (document.getStoragePath() != null) {
                Path stored = Path.of(document.getStoragePath());
                Files.deleteIfExists(stored);
                Files.deleteIfExists(stored.getParent());
            }
        } catch (IOException e) {
            log.warn("Could not remove stored file for {}: {}", id, e.getMessage());
        }
    }

    @Transactional
    public DocumentResponse reindex(UUID id, String department) throws IOException {
        KnowledgeDocument document = findVisible(id, department);
        if (document.getStoragePath() == null || !Files.exists(Path.of(document.getStoragePath()))) {
            throw new DocumentProcessingException("Original file is no longer available; upload it again");
        }
        String text = extractor.extract(Files.newInputStream(Path.of(document.getStoragePath())));
        document.setStatus(DocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        document.setCharacterCount(text.length());
        publisher.publishIngestion(toEvent(document, text));
        return DocumentResponse.from(document);
    }

    @Transactional
    public void applyIndexingResult(DocumentStatusEvent event) {
        UUID id;
        try {
            id = UUID.fromString(event.documentId());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring status for malformed document id {}", event.documentId());
            return;
        }
        repository.findById(id).ifPresentOrElse(document -> {
            boolean indexed = "INDEXED".equalsIgnoreCase(event.status());
            document.setStatus(indexed ? DocumentStatus.INDEXED : DocumentStatus.FAILED);
            document.setChunkCount(event.chunkCount() == null ? 0 : event.chunkCount());
            document.setErrorMessage(indexed ? null : event.error());
            document.setIndexedAt(indexed ? LocalDateTime.now() : null);
        }, () -> log.warn("Status received for unknown document {}", id));
    }

    // ---------------------------------------------------------------- helpers
    private KnowledgeDocument findVisible(UUID id, String department) {
        return repository.findById(id)
                .filter(d -> visibleDepartments(department).contains(d.getDepartment()))
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    private DocumentIngestionEvent toEvent(KnowledgeDocument d, String text) {
        return new DocumentIngestionEvent(d.getId().toString(), d.getTitle(), d.getFileName(), d.getDepartment(),
                List.copyOf(d.getTags()), d.getOwnerId(), text);
    }

    static List<String> visibleDepartments(String department) {
        String dept = normaliseDepartment(department);
        return PUBLIC_DEPARTMENT.equals(dept) ? List.of(PUBLIC_DEPARTMENT) : List.of(dept, PUBLIC_DEPARTMENT);
    }

    static String normaliseDepartment(String department) {
        return department == null || department.isBlank() ? PUBLIC_DEPARTMENT : department.trim().toUpperCase();
    }

    static List<String> normaliseTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(tags.stream()
                .flatMap(t -> Arrays.stream(t.split(",")))
                .map(t -> t.trim().toLowerCase())
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList());
    }

    static String safeFileName(String original) {
        String name = original == null ? "document" : Path.of(original).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        return name.isEmpty() ? "document" : name;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }
}
