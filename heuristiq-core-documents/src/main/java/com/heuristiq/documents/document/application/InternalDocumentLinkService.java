package com.heuristiq.documents.document.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.audit.DocumentAuditAction;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.exception.DmsException;

/**
 * Download links for another platform service (for example Rider_CommerceService showing a product's picture to shoppers). An
 * internal caller is trusted across organizations elsewhere in DMS, so here the organization is required from the caller and a
 * document of another organization answers "not found", exactly like a missing one.
 */
@Service
public class InternalDocumentLinkService {

    /** A link to the document and what it is. {@code url} is pre-signed storage or the DMS public-token path. */
    public record Link(String url, Instant expiresAt, String contentType, long sizeBytes) {
    }

    private final DocumentMetadataStore repository;
    private final DocumentObjectService objectService;
    private final DocumentDownloadTokenService downloadTokenService;
    private final DocumentAuditService auditService;

    public InternalDocumentLinkService(DocumentMetadataStore repository, DocumentObjectService objectService,
                                       DocumentDownloadTokenService downloadTokenService, DocumentAuditService auditService) {
        this.repository = repository;
        this.objectService = objectService;
        this.downloadTokenService = downloadTokenService;
        this.auditService = auditService;
    }

    /**
     * @throws DmsException bad request without an organization; not found when the document is missing, deleted or belongs to
     *                      another organization
     */
    public Link link(String tenantId, String documentId) {
        if (!StringUtils.hasText(tenantId)) {
            throw DmsException.badRequest(InternalLinkConstants.TENANT_REQUIRED);
        }
        DocumentMetadata metadata = repository.findByIdAndDeletedFalse(documentId)
                .filter(found -> tenantId.equals(found.getTenantId()))
                .orElseThrow(() -> DmsException.notFound(InternalLinkConstants.NOT_FOUND));
        Link link = objectService.presignDownload(metadata.getObjectKey(), metadata.getProvider())
                .map(presigned -> new Link(presigned.url(), presigned.expiresAt(), metadata.getContentType(), metadata.getSizeBytes()))
                .orElseGet(() -> {
                    String token = downloadTokenService.issueToken(metadata.getId(), metadata.getObjectKey());
                    long expiresAt = downloadTokenService.verify(token).expiresAt();
                    return new Link(DmsCommonConstants.PUBLIC_DOWNLOAD_PATH + token, Instant.ofEpochSecond(expiresAt),
                            metadata.getContentType(), metadata.getSizeBytes());
                });
        auditService.record(DocumentAuditAction.DOWNLOAD_URL_ISSUED, metadata, DocumentConstants.STATUS_SUCCESS,
                InternalLinkConstants.AUDIT_MESSAGE);
        return link;
    }
}
