package com.enterprise.knowledge.document;

import com.enterprise.knowledge.document.dto.DocumentResponse;
import com.enterprise.knowledge.document.messaging.DocumentEventPublisher;
import com.enterprise.knowledge.document.messaging.DocumentIngestionEvent;
import com.enterprise.knowledge.document.messaging.DocumentStatusEvent;
import com.enterprise.knowledge.document.service.DocumentProcessingException;
import com.enterprise.knowledge.document.service.DocumentService;
import com.enterprise.knowledge.document.service.ForbiddenException;
import com.enterprise.knowledge.document.service.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class DocumentServiceTests {

    @Autowired
    private DocumentService documentService;

    @MockitoBean
    private DocumentEventPublisher publisher;

    private MockMultipartFile textFile(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadExtractsTextStoresMetadataAndQueuesIngestion() throws Exception {
        DocumentResponse doc = documentService.upload(
                textFile("leave-policy.txt", "Employees accrue 24 days of annual leave."),
                null, "hr", List.of("hr, Policy"), "user-1");

        assertThat(doc.title()).isEqualTo("leave-policy");
        assertThat(doc.department()).isEqualTo("HR");
        assertThat(doc.tags()).containsExactly("hr", "policy");
        assertThat(doc.status()).isEqualTo("PROCESSING");

        ArgumentCaptor<DocumentIngestionEvent> event = ArgumentCaptor.forClass(DocumentIngestionEvent.class);
        verify(publisher).publishIngestion(event.capture());
        assertThat(event.getValue().documentId()).isEqualTo(doc.id());
        assertThat(event.getValue().content()).contains("24 days of annual leave");
    }

    @Test
    void statusEventFromAiServiceMarksDocumentIndexed() throws Exception {
        DocumentResponse doc = documentService.upload(textFile("vpn.md", "# VPN\nUse SSO and MFA."),
                "VPN Guide", "ENGINEERING", null, "user-2");

        documentService.applyIndexingResult(new DocumentStatusEvent(doc.id(), "INDEXED", 3, null));

        DocumentResponse indexed = documentService.get(UUID.fromString(doc.id()), "ENGINEERING");
        assertThat(indexed.status()).isEqualTo("INDEXED");
        assertThat(indexed.chunkCount()).isEqualTo(3);
        assertThat(indexed.indexedAt()).isNotNull();
    }

    @Test
    void departmentDocumentsAreHiddenFromOtherDepartments() throws Exception {
        DocumentResponse doc = documentService.upload(textFile("salaries.txt", "Confidential salary bands"),
                "Salary Bands", "FINANCE", List.of("comp"), "user-3");

        assertThatThrownBy(() -> documentService.get(UUID.fromString(doc.id()), "ENGINEERING"))
                .isInstanceOf(NotFoundException.class);
        assertThat(documentService.search("salary", "ENGINEERING", null, 10)).isEmpty();
        assertThat(documentService.search("comp", "finance", null, 10)).extracting(DocumentResponse::id)
                .contains(doc.id());
    }

    @Test
    void onlyTheUploaderCanDelete() throws Exception {
        DocumentResponse doc = documentService.upload(textFile("handbook.txt", "Company handbook"),
                "Handbook", "ALL", null, "owner");
        UUID id = UUID.fromString(doc.id());

        assertThatThrownBy(() -> documentService.delete(id, "someone-else", "ALL"))
                .isInstanceOf(ForbiddenException.class);
        documentService.delete(id, "owner", "ALL");
        assertThatThrownBy(() -> documentService.get(id, "ALL")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void emptyDocumentsAreRejected() {
        assertThatThrownBy(() -> documentService.upload(textFile("blank.txt", "   \n  "), null, null, null, "u"))
                .isInstanceOf(DocumentProcessingException.class);
    }
}
