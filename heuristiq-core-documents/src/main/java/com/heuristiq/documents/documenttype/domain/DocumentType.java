package com.railcab.dms.documenttype.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One kind of document an organization accepts (for example a photo ID), with the rule an upload of that kind must
 * satisfy. Belongs to exactly one organization ({@code tenantId}); {@code code} is unique inside it and never changes.
 *
 * <p>Immutable. A record is never edited in place: an approved change produces a new value that is saved against the
 * {@code version} that was read (optimistic lock).
 *
 * @param id                      UUID string
 * @param tenantId                owning organization
 * @param code                    UPPER_SNAKE identifier, unique per organization, immutable
 * @param category                lower-case upload category this type belongs to (what uploads send as {@code category})
 * @param flow                    optional business flow, e.g. {@code MATRIMONY_ONBOARDING}
 * @param names                   display name per language code; English always present
 * @param allowedFormats          formats an upload of this type may have (at least one)
 * @param maxFileSizeMb           largest accepted file in megabytes
 * @param required                whether the flow requires this document
 * @param expiryYears             years after which the document expires, or null
 * @param retentionDays           days an upload of this type is kept from upload time (1-36500), or null to use the service default
 * @param scanRequired            true when uploads of this type must pass a malware scan (fail closed when none can run)
 * @param verificationRequired    true when a document of this type must be verified (configuration only: no provider is called yet)
 * @param verificationMethod      how it is verified; {@link VerificationMethod#NONE} exactly when verification is not required
 * @param active                  only active types can be used by uploads
 * @param version                 optimistic-lock version; null before the first save
 * @param createdAt               creation time
 * @param updatedAt               last change time
 * @param createdBy               maker of the creating change request
 * @param updatedBy               maker of the last change request
 * @param lastChangeRequestId     the change request that produced this state
 * @param recentChangeRequestIds  the most recent applied change request ids (newest last), for idempotency
 */
public record DocumentType(
        String id,
        String tenantId,
        String code,
        String category,
        String flow,
        Map<String, String> names,
        Set<DocumentFormat> allowedFormats,
        int maxFileSizeMb,
        boolean required,
        Integer expiryYears,
        Integer retentionDays,
        boolean scanRequired,
        boolean verificationRequired,
        VerificationMethod verificationMethod,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        String lastChangeRequestId,
        List<String> recentChangeRequestIds,
        String verificationProvider
) {


    /** Source-compatible constructor for records written before provider assignments existed. */
    public DocumentType(String id, String tenantId, String code, String category, String flow, Map<String, String> names, Set<DocumentFormat> allowedFormats, int maxFileSizeMb, boolean required, Integer expiryYears, Integer retentionDays, boolean scanRequired, boolean verificationRequired, VerificationMethod verificationMethod, boolean active, Long version, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, String lastChangeRequestId, List<String> recentChangeRequestIds) {
        this(id, tenantId, code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired, verificationRequired, verificationMethod, active, version, createdAt, updatedAt, createdBy, updatedBy, lastChangeRequestId, recentChangeRequestIds, null);
    }

    /** A missing method reads as {@link VerificationMethod#NONE}. */
    public DocumentType {
        verificationMethod = verificationMethod == null ? VerificationMethod.NONE : verificationMethod;
    }

    /**
     * A type without verification ({@code false} / {@link VerificationMethod#NONE}); kept for callers that predate the
     * verification fields.
     */
    public DocumentType(String id, String tenantId, String code, String category, String flow, Map<String, String> names,
                        Set<DocumentFormat> allowedFormats, int maxFileSizeMb, boolean required, Integer expiryYears,
                        Integer retentionDays, boolean scanRequired, boolean active, Long version, Instant createdAt,
                        Instant updatedAt, String createdBy, String updatedBy, String lastChangeRequestId,
                        List<String> recentChangeRequestIds) {
        this(id, tenantId, code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays,
                scanRequired, DocumentTypeConstants.DEFAULT_VERIFICATION_REQUIRED, VerificationMethod.NONE, active, version,
                createdAt, updatedAt, createdBy, updatedBy, lastChangeRequestId, recentChangeRequestIds);
    }

    /** Same record with the lock version replaced (used by adapters after a save). */
    public DocumentType withVersion(Long newVersion) {
        return new DocumentType(id, tenantId, code, category, flow, names, allowedFormats, maxFileSizeMb, required,
                expiryYears, retentionDays, scanRequired, verificationRequired, verificationMethod, active, newVersion, createdAt, updatedAt, createdBy, updatedBy, lastChangeRequestId,
                recentChangeRequestIds, verificationProvider);
    }
}
