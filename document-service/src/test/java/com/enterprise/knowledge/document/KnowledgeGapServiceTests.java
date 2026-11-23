package com.enterprise.knowledge.document;

import com.enterprise.knowledge.document.dto.KnowledgeGapRequest;
import com.enterprise.knowledge.document.dto.KnowledgeGapResponse;
import com.enterprise.knowledge.document.messaging.DocumentEventPublisher;
import com.enterprise.knowledge.document.model.GapStatus;
import com.enterprise.knowledge.document.service.KnowledgeGapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeGapServiceTests {

    @Autowired
    private KnowledgeGapService gapService;

    @MockitoBean
    private DocumentEventPublisher publisher;

    @Test
    void reportedGapsCanBeListedAndResolved() {
        KnowledgeGapResponse gap = gapService.report(
                new KnowledgeGapRequest("What is the parental leave policy?", "u1", "No relevant passages"));
        assertThat(gapService.list(GapStatus.OPEN, 10)).extracting(KnowledgeGapResponse::id).contains(gap.id());

        KnowledgeGapResponse resolved = gapService.resolve(UUID.fromString(gap.id()), "Uploaded parental-leave.pdf");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(gapService.list(GapStatus.OPEN, 10)).extracting(KnowledgeGapResponse::id).doesNotContain(gap.id());
    }
}
