package com.heuristiq.documents.document.application;

import com.heuristiq.documents.centralaudit.CentralAuditPublisher;
import com.heuristiq.documents.centralaudit.DmsAuditEvent;
import com.heuristiq.documents.document.audit.DocumentAuditAction;
import com.heuristiq.documents.document.audit.DocumentAuditEvent;
import com.heuristiq.documents.document.audit.DocumentAuditStore;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");

    private final DocumentAuditStore local = mock(DocumentAuditStore.class);
    private final CentralAuditPublisher publisher = mock(CentralAuditPublisher.class);
    private final DocumentAuditService service = new DocumentAuditService(local, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal("u1", "ADMIN", "org-a"), null, List.of()));
        MDC.put("correlationId", "corr-1");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static DocumentMetadata document(String tenant) {
        return DocumentMetadata.builder().id("doc-1").tenantId(tenant).category("kyc").documentType("PHOTO_ID").ownerId("u1")
                .provider("s3").sizeBytes(10).scanStatus("CLEAN").objectKey("k").build();
    }

    private DmsAuditEvent published() {
        ArgumentCaptor<DmsAuditEvent> captor = ArgumentCaptor.forClass(DmsAuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void documentEventUsesTheDocumentsOrganizationAndIsKeptLocally() {
        service.record(DocumentAuditAction.DELETE, document("org-doc"), "SUCCESS", "Document deleted");

        DmsAuditEvent event = published();
        assertEquals("org-doc", event.tenantId());
        assertEquals("doc-1", event.subjectId());
        assertEquals("DOCUMENT", event.subjectType());
        assertEquals("corr-1", event.correlationId());
        assertEquals("SUCCESS", event.outcome());
        assertTrue(event.eventId().matches("[A-Za-z0-9_-]{8,64}"), event.eventId());
        assertEquals(NOW.toString(), event.occurredAt());
        ArgumentCaptor<DocumentAuditEvent> kept = ArgumentCaptor.forClass(DocumentAuditEvent.class);
        verify(local).save(kept.capture());
        assertEquals("doc-1", kept.getValue().getDocumentId());
    }

    @Test
    void legacyDocumentWithoutOrganizationFallsBackToTheSessionOrganization() {
        service.record(DocumentAuditAction.DOWNLOAD, document(null), "SUCCESS", "Document downloaded");

        assertEquals("org-a", published().tenantId());
    }

    @Test
    void purgeIsASystemEventOfTheDocumentsOrganization() {
        SecurityContextHolder.clearContext();

        service.recordPurge(document("org-doc"), "Deleted document purged after retention");

        DmsAuditEvent event = published();
        assertEquals("SYSTEM", event.userId());
        assertEquals("org-doc", event.tenantId());
        assertEquals("PURGED_AFTER_RETENTION", event.reasonCode());
    }

    @Test
    void outcomeMapsRefusalsToFailureOrDenied() {
        assertEquals("SUCCESS", DocumentAuditService.outcomeOf("SUCCESS", null));
        assertEquals("FAILURE", DocumentAuditService.outcomeOf("REJECTED", "FILE_TOO_LARGE"));
        assertEquals("DENIED", DocumentAuditService.outcomeOf("REJECTED", "ACCESS_DENIED"));
        assertEquals("DENIED", DocumentAuditService.outcomeOf("REJECTED", "NO_ORGANIZATION"));
    }

    @Test
    void longMessagesAreCutAndFieldsAreBounded() {
        service.recordUploadEvent(new DocumentAuditService.UploadEventInfo(DocumentAuditAction.UPLOAD_REJECTED, "REJECTED",
                "c".repeat(500), "u1", "T".repeat(500), "FILE_TOO_LARGE", "m".repeat(1000), null, 5L, 9L, null));

        DmsAuditEvent event = published();
        assertEquals(250, event.details().get("message").toString().length());
        assertEquals(128, event.details().get("category").toString().length());
        assertEquals(128, event.details().get("documentType").toString().length());
        assertNull(event.subjectId());
        assertNull(event.subjectType());
    }

    @Test
    void failuresOnEitherSideAreSwallowed() {
        doThrow(new IllegalStateException("local down")).when(local).save(any());
        doThrow(new IllegalStateException("queue down")).when(publisher).publish(any());

        assertDoesNotThrow(() -> service.record(DocumentAuditAction.UPLOAD, document("org-a"), "SUCCESS", "Document uploaded"));
        assertDoesNotThrow(() -> service.recordPurge(document("org-a"), "purged"));
        assertDoesNotThrow(() -> service.recordUploadEvent(new DocumentAuditService.UploadEventInfo(
                DocumentAuditAction.UPLOAD_REJECTED, "REJECTED", "kyc", "u1", null, "X", "m", null, null, null, null)));
    }
}
