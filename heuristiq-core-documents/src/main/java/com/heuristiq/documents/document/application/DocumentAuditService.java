package com.railcab.dms.document.application;

import com.railcab.dms.centralaudit.CentralAuditConstants;
import com.railcab.dms.centralaudit.CentralAuditPublisher;
import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.centralaudit.DmsAuditEvent;
import com.railcab.dms.document.audit.DocumentAuditAction;
import com.railcab.dms.document.audit.DocumentAuditEvent;
import com.railcab.dms.document.audit.DocumentAuditStore;
import com.railcab.dms.document.audit.DocumentReasonCodes;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.security.DmsPrincipal;
import com.railcab.dms.security.DmsSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records what happens to documents. Events about a stored document are kept in DMS's own audit store (as before) and,
 * for every event, queued for the central audit service through the outbox. An event never carries file contents,
 * secrets or personal data, only identifiers, a stable reason code and a short plain-English message. Recording must
 * never fail or change the outcome of the operation being recorded: every failure is logged with context and swallowed.
 */
@Service
public class DocumentAuditService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAuditService.class);

    private final DocumentAuditStore repository;
    private final CentralAuditPublisher publisher;
    private final Clock clock;

    public DocumentAuditService(DocumentAuditStore repository, CentralAuditPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** What is known about an upload that has no stored document (a refusal). */
    public record UploadEventInfo(DocumentAuditAction action, String status, String category, String ownerId,
                                  String documentType, String reasonCode, String message, String storageProvider,
                                  Long durationMs, Long sizeBytes, String scanVerdict) {
    }

    /** Records an event about a stored document. */
    public void record(DocumentAuditAction action, DocumentMetadata metadata, String status, String message) {
        record(action, metadata, status, null, message, null);
    }

    /**
     * Records an event about a stored document.
     *
     * @param reasonCode stable machine code, or null
     * @param durationMs how long the operation took, or null
     */
    public void record(DocumentAuditAction action, DocumentMetadata metadata, String status, String reasonCode,
                       String message, Long durationMs) {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        saveLocal(action, metadata, status, message, principal);
        String tenantId = StringUtils.hasText(metadata.getTenantId()) ? metadata.getTenantId() : principal.tenantId();
        publishCentral(action, status, reasonCode, message, durationMs, tenantId, metadata.getId(), principal.userId(),
                principal.role(), metadata.getCategory(), metadata.getDocumentType(), metadata.getProvider(),
                metadata.getSizeBytes(), scanVerdictOf(action, metadata));
    }

    /** Records that the retention job purged a document's metadata (no user is involved). */
    public void recordPurge(DocumentMetadata metadata, String message) {
        publishCentral(DocumentAuditAction.PURGE, DocumentConstants.STATUS_SUCCESS, DocumentReasonCodes.PURGED_AFTER_RETENTION, message, null,
                metadata.getTenantId(), metadata.getId(), DocumentConstants.SYSTEM_ACTOR, DocumentConstants.SYSTEM_ACTOR, metadata.getCategory(),
                metadata.getDocumentType(), metadata.getProvider(), metadata.getSizeBytes(), null);
    }

    /**
     * Records an event for an upload that has no document yet (refused, or its scan result before it was stored). The
     * organization comes from the session; a caller without one cannot be shown to anyone, so nothing is queued.
     */
    public void recordUploadEvent(UploadEventInfo info) {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        publishCentral(info.action(), info.status(), info.reasonCode(), info.message(), info.durationMs(),
                principal.tenantId(), null, principal.userId(), principal.role(), limit(info.category(), DocumentConstants.AUDIT_MAX_FIELD_LENGTH),
                limit(info.documentType(), DocumentConstants.AUDIT_MAX_FIELD_LENGTH), info.storageProvider(), info.sizeBytes(), info.scanVerdict());
    }

    private void saveLocal(DocumentAuditAction action, DocumentMetadata metadata, String status, String message,
                           DmsPrincipal principal) {
        try {
            DocumentAuditEvent event = new DocumentAuditEvent();
            event.setDocumentId(metadata.getId());
            event.setObjectKey(metadata.getObjectKey());
            event.setOwnerId(metadata.getOwnerId());
            event.setCategory(metadata.getCategory());
            event.setAction(action);
            event.setActorId(principal.userId());
            event.setActorRole(principal.role());
            event.setStatus(status);
            event.setMessage(limit(message, DocumentConstants.AUDIT_MAX_MESSAGE_LENGTH));
            event.setOccurredAt(clock.instant());
            repository.save(event);
        } catch (RuntimeException ex) {
            log.warn("Unable to persist DMS audit event action={} documentId={}", action, metadata.getId(), ex);
        }
    }

    private void publishCentral(DocumentAuditAction action, String status, String reasonCode, String message,
                                Long durationMs, String tenantId, String documentId, String userId, String role,
                                String category, String documentType, String storageProvider, Long sizeBytes,
                                String scanVerdict) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            put(details, CentralAuditConstants.D_ACTION, action.name());
            put(details, CentralAuditConstants.D_DOCUMENT_TYPE, documentType);
            put(details, CentralAuditConstants.D_CATEGORY, category);
            put(details, CentralAuditConstants.D_REASON_CODE, reasonCode);
            put(details, CentralAuditConstants.D_STORAGE_PROVIDER, storageProvider);
            put(details, CentralAuditConstants.D_SCAN_VERDICT, scanVerdict);
            if (sizeBytes != null) {
                details.put(CentralAuditConstants.D_SIZE_BYTES, sizeBytes);
            }
            put(details, CentralAuditConstants.D_MESSAGE, limit(message, CentralAuditConstants.DETAIL_MAX_LENGTH));
            publisher.publish(new DmsAuditEvent(
                    CentralAuditConstants.EVENT_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                    CentralAuditConstants.EVENT_TYPE,
                    clock.instant().toString(),
                    CentralAuditConstants.SOURCE_SERVICE,
                    userId,
                    role,
                    CentralAuditConstants.FEATURE,
                    CentralAuditConstants.MODULE,
                    outcomeOf(status, reasonCode),
                    durationMs,
                    MDC.get(DmsCommonConstants.MDC_KEY),
                    CentralAuditConstants.APP_CHANNEL,
                    tenantId,
                    documentId == null ? null : CentralAuditConstants.SUBJECT_TYPE_DOCUMENT,
                    documentId,
                    reasonCode,
                    details));
        } catch (RuntimeException ex) {
            log.warn("Unable to queue DMS audit event action={} documentId={}", action, documentId, ex);
        }
    }

    /** SUCCESS for a worked operation, DENIED for an access refusal, FAILURE for every other refusal. */
    static String outcomeOf(String status, String reasonCode) {
        if (!DocumentConstants.STATUS_REJECTED.equals(status)) {
            return CentralAuditConstants.OUTCOME_SUCCESS;
        }
        return DocumentReasonCodes.ACCESS_DENIED.equals(reasonCode) || DocumentReasonCodes.NO_ORGANIZATION.equals(reasonCode)
                ? CentralAuditConstants.OUTCOME_DENIED : CentralAuditConstants.OUTCOME_FAILURE;
    }

    private static String scanVerdictOf(DocumentAuditAction action, DocumentMetadata metadata) {
        return action == DocumentAuditAction.UPLOAD || action == DocumentAuditAction.SCAN_RESULT ? metadata.getScanStatus() : null;
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
