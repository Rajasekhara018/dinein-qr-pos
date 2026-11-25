package com.heuristiq.documents.document.application;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.audit.DocumentAuditAction;
import com.heuristiq.documents.document.audit.DocumentReasonCodes;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.dto.DocumentDownloadUrlResponse;
import com.heuristiq.documents.document.dto.DocumentResponse;
import com.heuristiq.documents.document.dto.RUpload;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.security.DmsPrincipal;
import com.heuristiq.documents.security.DmsSecurityContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentValidationService validationService;
    private final DocumentObjectService objectService;
    private final DocumentAccessService accessService;
    private final DocumentAuditService auditService;
    private final DocumentScanService scanService;
    private final DocumentLifecycleService lifecycleService;
    private final DocumentDownloadTokenService downloadTokenService;
    private final DocumentMetadataStore repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    /** Today in the expiry zone (D12); null in tests that build the service without it (the clock's UTC day is used). */
    private final com.heuristiq.documents.expiry.application.DocumentExpiryService expiry;

    public DocumentService(
            DocumentValidationService validationService,
            DocumentObjectService objectService,
            DocumentAccessService accessService,
            DocumentAuditService auditService,
            DocumentScanService scanService,
            DocumentLifecycleService lifecycleService,
            DocumentDownloadTokenService downloadTokenService,
            DocumentMetadataStore repository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this(validationService, objectService, accessService, auditService, scanService, lifecycleService, downloadTokenService,
                repository, meterRegistry, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentService(
            DocumentValidationService validationService,
            DocumentObjectService objectService,
            DocumentAccessService accessService,
            DocumentAuditService auditService,
            DocumentScanService scanService,
            DocumentLifecycleService lifecycleService,
            DocumentDownloadTokenService downloadTokenService,
            DocumentMetadataStore repository,
            MeterRegistry meterRegistry,
            Clock clock,
            com.heuristiq.documents.expiry.application.DocumentExpiryService expiry
    ) {
        this.expiry = expiry;
        this.validationService = validationService;
        this.objectService = objectService;
        this.accessService = accessService;
        this.auditService = auditService;
        this.scanService = scanService;
        this.lifecycleService = lifecycleService;
        this.downloadTokenService = downloadTokenService;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public DocumentResponse upload(MultipartFile file, String category, String ownerId, String password, String expectedChecksum) {
        return upload(file, category, ownerId, password, expectedChecksum, null);
    }

    /**
     * Uploads a document, optionally validated against an organization's document type.
     *
     * @param documentType optional document type code; when blank the category rule applies exactly as before
     */
    public DocumentResponse upload(MultipartFile file, String category, String ownerId, String password,
                                   String expectedChecksum, String documentType) {
        return upload(file, category, ownerId, password, expectedChecksum, documentType, null);
    }

    /**
     * Uploads a document with the last day it is valid (D12), as the uploader read it from the document; a reviewer confirms or
     * corrects it later ({@link #confirmValidUntil}).
     *
     * @param validUntil optional; a date before today is refused (an expired document cannot be uploaded)
     */
    public DocumentResponse upload(MultipartFile file, String category, String ownerId, String password,
                                   String expectedChecksum, String documentType, LocalDate validUntil) {
        Instant started = clock.instant();
        try {
            if (validUntil != null && validUntil.isBefore(today())) {
                throw DmsException.badRequest(DocumentConstants.VALID_UNTIL_IN_PAST, DocumentReasonCodes.VALIDATION_FAILED);
            }
            DocumentResponse response = uploadInternal(file, category, ownerId, password, expectedChecksum, documentType, validUntil,
                    started);
            meterRegistry.counter(DocumentConstants.METRIC_UPLOADS, DocumentConstants.TAG_PROVIDER, response.provider(),
                    DocumentConstants.TAG_OUTCOME, DocumentConstants.OUTCOME_SUCCESS).increment();
            return response;
        } catch (DmsException e) {
            meterRegistry.counter(DocumentConstants.METRIC_UPLOADS, DocumentConstants.TAG_OUTCOME,
                    DocumentConstants.OUTCOME_FAILED, DocumentConstants.TAG_REASON, e.getStatus().name()).increment();
            recordRejection(file, category, ownerId, documentType,
                    e.getReasonCode() != null ? e.getReasonCode() : DocumentReasonCodes.VALIDATION_FAILED, e.getMessage(), started);
            throw e;
        } catch (RuntimeException e) {
            meterRegistry.counter(DocumentConstants.METRIC_UPLOADS, DocumentConstants.TAG_OUTCOME,
                    DocumentConstants.OUTCOME_FAILED, DocumentConstants.TAG_REASON, DocumentConstants.REASON_STORAGE_ERROR).increment();
            recordRejection(file, category, ownerId, documentType, DocumentReasonCodes.UPLOAD_FAILED,
                    "Upload failed unexpectedly", started);
            throw e;
        }
    }

    /** Writes the UPLOAD_REJECTED event (and a SCAN_RESULT event when the scan step refused it). Never throws. */
    private void recordRejection(MultipartFile file, String category, String ownerId, String documentType,
                                 String reasonCode, String message, Instant started) {
        long durationMs = Duration.between(started, clock.instant()).toMillis();
        Long sizeBytes = file == null ? null : file.getSize();
        auditService.recordUploadEvent(new DocumentAuditService.UploadEventInfo(DocumentAuditAction.UPLOAD_REJECTED,
                DocumentConstants.STATUS_REJECTED, category, ownerId, documentType, reasonCode, message, null,
                durationMs, sizeBytes, null));
        if (DocumentReasonCodes.SCAN_REJECTIONS.contains(reasonCode)) {
            auditService.recordUploadEvent(new DocumentAuditService.UploadEventInfo(DocumentAuditAction.SCAN_RESULT,
                    DocumentConstants.STATUS_REJECTED, category, ownerId, documentType, reasonCode,
                    message + " (provider " + scanService.currentProviderName() + ")", null, durationMs, sizeBytes,
                    scanVerdictOf(reasonCode)));
        }
    }

    private static String scanVerdictOf(String reasonCode) {
        return switch (reasonCode) {
            case DocumentReasonCodes.MALWARE_DETECTED -> DocumentConstants.VERDICT_INFECTED;
            case DocumentReasonCodes.SCAN_UNAVAILABLE, DocumentReasonCodes.SCAN_INCONCLUSIVE -> DocumentConstants.VERDICT_NO_VERDICT;
            case DocumentReasonCodes.PASSWORD_PROTECTED_NOT_SCANNABLE -> DocumentConstants.VERDICT_NOT_SCANNABLE;
            default -> DocumentConstants.VERDICT_BLOCKED;
        };
    }

    private DocumentResponse uploadInternal(MultipartFile file, String category, String ownerId, String password,
                                            String expectedChecksum, String documentType, LocalDate validUntil, Instant started) {
        Optional<DocumentTypeRule> typeRule = validationService.validate(file, category, expectedChecksum, documentType);
        // A named type decides whether the scan is mandatory (fail closed); an upload with no type keeps the old behaviour.
        boolean scanRequired = typeRule.map(DocumentTypeRule::scanRequired).orElse(false);
        DocumentScanService.ScanResult scan = scanService.scan(file, password, scanRequired);
        DmsPrincipal caller = DmsSecurityContext.currentPrincipal();
        String safeCategory = DocumentObjectKeys.safePathPart(category);
        String safeOwnerId = DocumentObjectKeys.safePathPart(ownerId);
        String tenantSegment = DocumentObjectKeys.tenantSegment(caller.tenantId());
        String typeSegment = DocumentObjectKeys.documentTypeSegment(typeRule.map(DocumentTypeRule::documentTypeCode).orElse(null));
        accessService.assertCanUpload(safeOwnerId);
        String storedFileName = UUID.randomUUID().toString().substring(0, DocumentConstants.STORED_NAME_PREFIX_LENGTH) + "_"
                + DocumentObjectKeys.safeFileName(file.getOriginalFilename());
        String objectKey = DocumentObjectKeys.build(tenantSegment, safeCategory, typeSegment, safeOwnerId, storedFileName);

        DocumentObjectService.StoredDocument stored = storeOrTag(objectKey, file);

        DocumentMetadata metadata = DocumentMetadata.builder()
                .category(safeCategory)
                .documentType(typeRule.map(DocumentTypeRule::documentTypeCode).orElse(null))
                .ownerId(safeOwnerId)
                .tenantId(caller.tenantId())
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .objectKey(stored.objectKey())
                .publicUrl(stored.publicUrl())
                .provider(stored.provider())
                .versionId(stored.versionId())
                .checksumSha256(stored.checksumSha256())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .status(DocumentConstants.STATUS_UPLOADED)
                .uploadedBy(caller.userId())
                .encryption(stored.encryption())
                .scanStatus(scan.status())
                .scanMessage(scan.message())
                .validUntil(validUntil)
                .createdAt(clock.instant())
                .build();
        lifecycleService.applyDefaults(metadata, typeRule.map(DocumentTypeRule::retentionDays).orElse(null));

        DocumentMetadata saved = repository.save(metadata);
        long durationMs = Duration.between(started, clock.instant()).toMillis();
        auditService.record(DocumentAuditAction.UPLOAD, saved, DocumentConstants.STATUS_SUCCESS, null,
                "Document uploaded", durationMs);
        auditService.record(DocumentAuditAction.SCAN_RESULT, saved, DocumentConstants.STATUS_SUCCESS,
                scanReason(scan.status()), "Scan " + scan.status() + " (provider " + scan.provider() + ")", null);
        return toResponse(saved);
    }

    /** Stores the object; a storage failure that carries no reason code is tagged STORAGE_FAILED. */
    private DocumentObjectService.StoredDocument storeOrTag(String objectKey, MultipartFile file) {
        try {
            return objectService.store(objectKey, file);
        } catch (DmsException e) {
            if (e.getReasonCode() == null) {
                throw DmsException.badRequest(e.getMessage(), DocumentReasonCodes.STORAGE_FAILED);
            }
            throw e;
        }
    }

    private static String scanReason(String scanStatus) {
        return switch (scanStatus == null ? "" : scanStatus) {
            case DocumentConstants.SCAN_CLEAN -> DocumentReasonCodes.SCAN_CLEAN;
            case DocumentConstants.SCAN_SKIPPED_PASSWORD_PROTECTED -> DocumentReasonCodes.SCAN_SKIPPED_PASSWORD_PROTECTED;
            default -> DocumentReasonCodes.SCAN_SKIPPED_DISABLED;
        };
    }

    public Resource loadByObjectKey(String objectKey) {
        Instant started = clock.instant();
        try {
            DocumentMetadata metadata = repository.findByObjectKeyAndDeletedFalse(objectKey)
                    .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
            accessService.assertCanAccess(metadata);
            Resource resource = objectService.load(objectKey, metadata.getProvider());
            auditService.record(DocumentAuditAction.DOWNLOAD, metadata, DocumentConstants.STATUS_SUCCESS, null,
                    "Document downloaded", Duration.between(started, clock.instant()).toMillis());
            meterRegistry.counter(DocumentConstants.METRIC_DOWNLOADS, DocumentConstants.TAG_PROVIDER, metadata.getProvider(),
                    DocumentConstants.TAG_OUTCOME, DocumentConstants.OUTCOME_SUCCESS).increment();
            return resource;
        } catch (DmsException e) {
            meterRegistry.counter(DocumentConstants.METRIC_DOWNLOADS, DocumentConstants.TAG_OUTCOME,
                    DocumentConstants.OUTCOME_FAILED, DocumentConstants.TAG_REASON, e.getStatus().name()).increment();
            throw e;
        }
    }

    public Resource loadByDownloadToken(String token) {
        try {
            DocumentDownloadTokenService.TokenClaims claims = downloadTokenService.verify(token);
            DocumentMetadata metadata = repository.findByIdAndDeletedFalse(claims.documentId())
                    .filter(candidate -> candidate.getObjectKey().equals(claims.objectKey()))
                    .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
            Resource resource = objectService.load(metadata.getObjectKey(), metadata.getProvider());
            auditService.record(DocumentAuditAction.DOWNLOAD, metadata, DocumentConstants.STATUS_SUCCESS, "Document downloaded by signed token");
            meterRegistry.counter(DocumentConstants.METRIC_DOWNLOADS, DocumentConstants.TAG_PROVIDER, metadata.getProvider(),
                    DocumentConstants.TAG_OUTCOME, DocumentConstants.OUTCOME_SUCCESS).increment();
            return resource;
        } catch (DmsException e) {
            meterRegistry.counter(DocumentConstants.METRIC_DOWNLOADS, DocumentConstants.TAG_OUTCOME,
                    DocumentConstants.OUTCOME_FAILED, DocumentConstants.TAG_REASON, e.getStatus().name()).increment();
            throw e;
        }
    }

    public DocumentDownloadUrlResponse createDownloadUrl(String id) {
        DocumentMetadata metadata = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
        accessService.assertCanAccess(metadata);

        DocumentDownloadUrlResponse response = objectService.presignDownload(metadata.getObjectKey(), metadata.getProvider())
                .map(presigned -> new DocumentDownloadUrlResponse(presigned.url(), presigned.expiresAt()))
                .orElseGet(() -> appTokenDownloadUrl(metadata));
        auditService.record(DocumentAuditAction.DOWNLOAD_URL_ISSUED, metadata, DocumentConstants.STATUS_SUCCESS,
                "Download link issued");
        return response;
    }

    private DocumentDownloadUrlResponse appTokenDownloadUrl(DocumentMetadata metadata) {
        String token = downloadTokenService.issueToken(metadata.getId(), metadata.getObjectKey());
        long expiresAt = downloadTokenService.verify(token).expiresAt();
        return new DocumentDownloadUrlResponse(DmsCommonConstants.PUBLIC_DOWNLOAD_PATH + token, Instant.ofEpochSecond(expiresAt));
    }

    public DocumentResponse getMetadata(String id) {
        return repository.findByIdAndDeletedFalse(id)
                .map(metadata -> {
                    accessService.assertCanAccess(metadata);
                    return metadata;
                })
                .map(this::toResponse)
                .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
    }

    /** Same lookup as {@link #getMetadata(String)}, mapped onto the common RUpload shape. */
    public RUpload getMetadataAsRUpload(String id) {
        return RUpload.from(getMetadata(id));
    }

    public List<DocumentResponse> list(String category, String ownerId) {
        String safeOwnerId = DocumentObjectKeys.safePathPart(ownerId);
        accessService.assertCanUpload(safeOwnerId);
        return repository.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(DocumentObjectKeys.safePathPart(category), safeOwnerId)
                .stream()
                .filter(accessService::inCallersOrganization)
                .map(this::toResponse)
                .toList();
    }

    public void delete(String id) {
        DocumentMetadata metadata = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
        accessService.assertCanAccess(metadata);
        lifecycleService.assertCanDelete(metadata);
        objectService.delete(metadata.getObjectKey(), metadata.getProvider());
        metadata.setDeleted(true);
        metadata.setDeletedAt(clock.instant());
        metadata.setDeletedBy(DmsSecurityContext.currentPrincipal().userId());
        metadata.setStatus(DocumentConstants.STATUS_DELETED);
        DocumentMetadata saved = repository.save(metadata);
        auditService.record(DocumentAuditAction.DELETE, saved, DocumentConstants.STATUS_SUCCESS, "Document deleted");
    }

    public DocumentResponse updateLegalHold(String id, boolean enabled) {
        DocumentMetadata metadata = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
        accessService.assertCanAccess(metadata);
        lifecycleService.applyLegalHold(metadata, enabled);
        DocumentMetadata saved = repository.save(metadata);
        auditService.record(DocumentAuditAction.LEGAL_HOLD, saved, DocumentConstants.STATUS_SUCCESS, "Legal hold set to " + enabled);
        return toResponse(saved);
    }

    /**
     * A reviewer confirms or corrects the last day a document is valid (D12), read from the document itself. A changed date restarts
     * its reminders. The document must belong to the reviewer's organization, and its owner cannot confirm their own date.
     *
     * @throws DmsException 404 for an unknown or deleted document of the organization, 403 for its owner, 400 without a date
     */
    public DocumentResponse confirmValidUntil(String id, LocalDate validUntil) {
        if (validUntil == null) {
            throw DmsException.badRequest(DocumentConstants.VALID_UNTIL_REQUIRED);
        }
        DmsPrincipal caller = DmsSecurityContext.currentPrincipal();
        DocumentMetadata metadata = repository.findByTenantIdAndId(caller.tenantId(), id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> DmsException.notFound("Document metadata not found"));
        accessService.assertCanAccess(metadata);
        if (caller.userId() != null && caller.userId().equals(metadata.getOwnerId())) {
            throw DmsException.forbidden(DocumentConstants.VALID_UNTIL_OWN_DOCUMENT);
        }
        LocalDate before = metadata.getValidUntil();
        if (!validUntil.equals(before)) {
            metadata.setExpiryNoticeDays(null);
        }
        metadata.setValidUntil(validUntil);
        metadata.setValidUntilConfirmed(true);
        metadata.setValidUntilConfirmedBy(caller.userId());
        metadata.setValidUntilConfirmedAt(clock.instant());
        DocumentMetadata saved = repository.save(metadata);
        auditService.record(DocumentAuditAction.VALID_UNTIL, saved, DocumentConstants.STATUS_SUCCESS,
                "Valid until confirmed: " + validUntil + (validUntil.equals(before) ? "" : " (was " + before + ")"));
        return toResponse(saved);
    }

    /** Today in the expiry zone (the clock's UTC day when built without the expiry service). */
    private LocalDate today() {
        return expiry != null ? expiry.today() : LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
    }

    private DocumentResponse toResponse(DocumentMetadata metadata) {
        return new DocumentResponse(
                metadata.getId(),
                metadata.getCategory(),
                metadata.getOwnerId(),
                metadata.getOriginalFileName(),
                metadata.getStoredFileName(),
                metadata.getPublicUrl(),
                metadata.getProvider(),
                metadata.getVersionId(),
                metadata.getChecksumSha256(),
                metadata.getContentType(),
                metadata.getSizeBytes(),
                metadata.getStatus(),
                metadata.getScanStatus(),
                metadata.getScanMessage(),
                metadata.getRetentionUntil(),
                metadata.isLegalHold(),
                metadata.getCreatedAt(),
                metadata.getObjectKey(),
                metadata.getValidUntil(),
                metadata.isValidUntilConfirmed()
        );
    }
}
