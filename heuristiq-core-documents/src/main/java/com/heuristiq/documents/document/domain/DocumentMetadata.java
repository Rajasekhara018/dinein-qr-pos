package com.heuristiq.documents.document.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.documents.search.SearchConstants;
import com.heuristiq.documents.search.SearchIndexable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-free domain model for a document's metadata. Persistence-specific representations
 * (Mongo document, JPA entity) live in {@code document.persistence.*} and map to/from this type
 * at the adapter boundary -- this class must never depend on a storage framework.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata implements SearchIndexable {

    private String id;
    private String category;
    private String ownerId;
    /** The organization the document belongs to (the uploader's session organization); null for a document stored before organizations. */
    private String tenantId;
    /** Code of the document type named at upload; null when none was named and for documents stored before it was recorded. */
    private String documentType;
    private String originalFileName;
    private String storedFileName;
    private String objectKey;
    private String publicUrl;
    private String provider;
    private String versionId;
    private String checksumSha256;
    private String contentType;
    private long sizeBytes;
    private String status;
    private String uploadedBy;
    private String scanStatus;
    private String scanMessage;
    /** Encryption at rest applied when stored: NONE, SSE_S3, SSE_KMS or PROVIDER_MANAGED; null for documents stored before it was recorded. */
    private String encryption;
    private Instant retentionUntil;
    private boolean legalHold;
    private Instant legalHoldChangedAt;
    private String legalHoldChangedBy;
    /**
     * D12: the last day the document is valid, as the uploader gave it or a reviewer confirmed or corrected it; null when it is not
     * tracked. Only {@link DocumentValidityConstants#TRACKED_CATEGORIES} feed the reminders and the driver block.
     */
    private LocalDate validUntil;
    /** True once a reviewer confirmed or corrected {@link #validUntil}; a captain-entered date is unconfirmed. */
    private boolean validUntilConfirmed;
    private String validUntilConfirmedBy;
    private Instant validUntilConfirmedAt;
    /** The smallest reminder (days before expiry) already sent for the current {@link #validUntil}; null when none was sent. */
    private Integer expiryNoticeDays;
    private boolean deleted;
    private Instant deletedAt;
    private String deletedBy;
    private Instant createdAt;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return SearchConstants.MODEL_DOCUMENT;
    }

    @JsonIgnore
    @Override
    public String getSearchModelKey() {
        return id;
    }

    @JsonIgnore
    @Override
    public Map<String, Object> getSearchIndexFields() {
        Map<String, Object> fields = new HashMap<>();
        // The organization, as every organization-owned model does, so search can limit results to the caller's own.
        // The owning user stays in text3.
        fields.put("ownerKey", tenantId);
        fields.put("text1", originalFileName);
        fields.put("text2", category);
        fields.put("text3", ownerId);
        fields.put("text4", status);
        fields.put("text5", contentType);
        fields.put("text6", provider);
        fields.put("text7", scanStatus);
        fields.put("number1", sizeBytes);
        if (createdAt != null) {
            fields.put("date1", createdAt.toString());
        }
        if (retentionUntil != null) {
            fields.put("date2", retentionUntil.toString());
        }
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
