package com.heuristiq.documents.document.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.heuristiq.documents.document.audit.DocumentAuditAction;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final DocumentValidationService validationService = mock(DocumentValidationService.class);
    private final DocumentObjectService objectService = mock(DocumentObjectService.class);
    private final DocumentAccessService accessService = mock(DocumentAccessService.class);
    private final DocumentAuditService auditService = mock(DocumentAuditService.class);
    private final DocumentScanService scanService = mock(DocumentScanService.class);
    private final DocumentLifecycleService lifecycleService = mock(DocumentLifecycleService.class);
    private final DocumentDownloadTokenService downloadTokenService = mock(DocumentDownloadTokenService.class);
    private final DocumentMetadataStore repository = mock(DocumentMetadataStore.class);
    private final DocumentService service = new DocumentService(
            validationService,
            objectService,
            accessService,
            auditService,
            scanService,
            lifecycleService,
            downloadTokenService,
            repository,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadStoresMetadataWithSanitizedPathAndAudits() {
        authenticate("owner-1", "CUSTOMER");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Profile Photo.JPG",
                "image/jpeg",
                "content".getBytes()
        );
        when(scanService.scan(file, null, false)).thenReturn(new DocumentScanService.ScanResult("CLEAN", "Basic scanner passed"));
        when(objectService.store(any(), any())).thenReturn(new DocumentObjectService.StoredDocument(
                "driver_docs/owner-1/generated_profile_photo.jpg",
                "/api/v1/dms/documents/driver_docs/owner-1/generated_profile_photo.jpg",
                "local",
                null,
                "abc123",
                "image/jpeg",
                7L,
                "NONE"
        ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(file, "driver docs", "owner-1", null, null);

        ArgumentCaptor<DocumentMetadata> metadata = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(repository).save(metadata.capture());
        assertEquals("driver_docs", metadata.getValue().getCategory());
        assertEquals("owner-1", metadata.getValue().getOwnerId());
        assertEquals("owner-1", metadata.getValue().getUploadedBy());
        assertEquals("CLEAN", metadata.getValue().getScanStatus());
        verify(lifecycleService).applyDefaults(metadata.getValue(), null);
        verify(auditService).record(DocumentAuditAction.UPLOAD, metadata.getValue(), "SUCCESS", null, "Document uploaded", 0L);
    }

    @Test
    void downloadRequiresMetadataBeforeLoadingObject() {
        when(repository.findByObjectKeyAndDeletedFalse("missing/key.pdf")).thenReturn(Optional.empty());

        assertThrows(DmsException.class, () -> service.loadByObjectKey("missing/key.pdf"));

        verify(objectService, never()).load(any(), any());
    }

    @Test
    void deleteSoftDeletesMetadataAndAudits() {
        authenticate("owner-1", "CUSTOMER");
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setId("doc-1");
        metadata.setOwnerId("owner-1");
        metadata.setObjectKey("owner-1/doc.pdf");
        when(repository.findByIdAndDeletedFalse("doc-1")).thenReturn(Optional.of(metadata));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete("doc-1");

        assertTrue(metadata.isDeleted());
        assertEquals("DELETED", metadata.getStatus());
        assertEquals("owner-1", metadata.getDeletedBy());
        verify(lifecycleService).assertCanDelete(metadata);
        verify(objectService).delete("owner-1/doc.pdf", null);
        verify(auditService).record(DocumentAuditAction.DELETE, metadata, "SUCCESS", "Document deleted");
    }

    @Test
    void tokenDownloadVerifiesClaimsBeforeLoadingObject() {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setId("doc-1");
        metadata.setObjectKey("owner-1/doc.pdf");
        metadata.setProvider("local");
        when(downloadTokenService.verify("token")).thenReturn(
                new DocumentDownloadTokenService.TokenClaims("doc-1", "owner-1/doc.pdf", 1000L)
        );
        when(repository.findByIdAndDeletedFalse("doc-1")).thenReturn(Optional.of(metadata));

        service.loadByDownloadToken("token");

        verify(objectService).load("owner-1/doc.pdf", "local");
        verify(auditService).record(
                DocumentAuditAction.DOWNLOAD,
                metadata,
                "SUCCESS",
                "Document downloaded by signed token"
        );
    }

    @Test
    void tokenDownloadRejectsObjectKeyMismatch() {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setId("doc-1");
        metadata.setObjectKey("owner-1/doc.pdf");
        when(downloadTokenService.verify("token")).thenReturn(
                new DocumentDownloadTokenService.TokenClaims("doc-1", "other/doc.pdf", 1000L)
        );
        when(repository.findByIdAndDeletedFalse("doc-1")).thenReturn(Optional.of(metadata));

        assertThrows(DmsException.class, () -> service.loadByDownloadToken("token"));

        verify(objectService, never()).load(any(), any());
    }

    private static void authenticate(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(userId, role, "org-1"),
                null,
                List.of()
        ));
    }
}
