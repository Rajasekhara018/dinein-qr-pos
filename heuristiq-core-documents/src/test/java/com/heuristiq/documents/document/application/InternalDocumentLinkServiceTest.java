package com.heuristiq.documents.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.audit.DocumentAuditAction;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.storage.StorageProvider;

/** A platform service gets a link only to a document of the organization it names. */
class InternalDocumentLinkServiceTest {

    private final DocumentMetadataStore repository = mock(DocumentMetadataStore.class);
    private final DocumentObjectService objectService = mock(DocumentObjectService.class);
    private final DocumentDownloadTokenService tokens = mock(DocumentDownloadTokenService.class);
    private final DocumentAuditService audit = mock(DocumentAuditService.class);
    private final InternalDocumentLinkService service = new InternalDocumentLinkService(repository, objectService, tokens, audit);
    private final DocumentMetadata metadata = new DocumentMetadata();

    @BeforeEach
    void setUp() {
        metadata.setId("doc-1");
        metadata.setTenantId("org-a");
        metadata.setObjectKey("key-1");
        metadata.setProvider("local");
        metadata.setContentType("image/png");
        metadata.setSizeBytes(1234);
        when(repository.findByIdAndDeletedFalse("doc-1")).thenReturn(Optional.of(metadata));
    }

    @Test
    void givesThePresignedStorageUrlWhenTheProviderHasOne() {
        Instant expires = Instant.parse("2026-11-23T10:05:00Z");
        when(objectService.presignDownload("key-1", "local"))
                .thenReturn(Optional.of(new StorageProvider.PresignedDownload("https://bucket/key-1?sig", expires)));

        InternalDocumentLinkService.Link link = service.link("org-a", "doc-1");

        assertThat(link.url()).isEqualTo("https://bucket/key-1?sig");
        assertThat(link.expiresAt()).isEqualTo(expires);
        assertThat(link.contentType()).isEqualTo("image/png");
        assertThat(link.sizeBytes()).isEqualTo(1234);
        verify(audit).record(eq(DocumentAuditAction.DOWNLOAD_URL_ISSUED), eq(metadata), anyString(), anyString());
    }

    @Test
    void otherwiseGivesTheDmsPublicTokenPath() {
        when(objectService.presignDownload(any(), any())).thenReturn(Optional.empty());
        when(tokens.issueToken("doc-1", "key-1")).thenReturn("tok");
        when(tokens.verify("tok")).thenReturn(new DocumentDownloadTokenService.TokenClaims("doc-1", "key-1", 1_800_000_000L));

        InternalDocumentLinkService.Link link = service.link("org-a", "doc-1");

        assertThat(link.url()).isEqualTo(DmsCommonConstants.PUBLIC_DOWNLOAD_PATH + "tok");
        assertThat(link.expiresAt()).isEqualTo(Instant.ofEpochSecond(1_800_000_000L));
    }

    @Test
    void anotherOrganizationsOrAMissingDocumentIsNotFoundAndNoOrganizationIsRefused() {
        assertThatThrownBy(() -> service.link("org-b", "doc-1")).isInstanceOf(DmsException.class)
                .hasMessage(InternalLinkConstants.NOT_FOUND);
        assertThatThrownBy(() -> service.link("org-a", "missing")).isInstanceOf(DmsException.class);
        assertThatThrownBy(() -> service.link(" ", "doc-1")).isInstanceOf(DmsException.class)
                .hasMessage(InternalLinkConstants.TENANT_REQUIRED);
        metadata.setTenantId(null);
        assertThatThrownBy(() -> service.link("org-a", "doc-1")).as("a document without organization is never shared")
                .isInstanceOf(DmsException.class);
        verify(objectService, never()).presignDownload(any(), any());
    }
}
