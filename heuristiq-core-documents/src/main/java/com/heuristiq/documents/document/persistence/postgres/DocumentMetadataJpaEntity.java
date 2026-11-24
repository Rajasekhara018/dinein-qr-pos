package com.heuristiq.documents.document.persistence.postgres;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "dms_documents", indexes = {
        @Index(name = "idx_dms_documents_category_owner", columnList = "category,ownerId"),
        @Index(name = "idx_dms_documents_tenant", columnList = "tenantId"),
        @Index(name = "idx_dms_documents_object_key", columnList = "objectKey"),
        @Index(name = "idx_dms_documents_deleted", columnList = "deleted,deletedAt"),
        @Index(name = "idx_dms_documents_tenant_created", columnList = "tenantId,createdAt"),
        @Index(name = "idx_dms_documents_tenant_owner", columnList = "tenantId,ownerId,createdAt"),
        @Index(name = "idx_dms_documents_tenant_category", columnList = "tenantId,category,createdAt"),
        @Index(name = "idx_dms_documents_tenant_type", columnList = "tenantId,documentType,createdAt"),
        @Index(name = "idx_dms_documents_tenant_status", columnList = "tenantId,status,createdAt"),
        @Index(name = "idx_dms_documents_tenant_scan", columnList = "tenantId,scanStatus,createdAt"),
        @Index(name = "idx_dms_documents_tenant_deleted", columnList = "tenantId,deleted,createdAt"),
        @Index(name = "idx_dms_documents_tenant_filename", columnList = "tenantId,originalFileName"),
        @Index(name = "idx_dms_documents_tenant_checksum", columnList = "tenantId,checksumSha256"),
        @Index(name = "idx_dms_documents_category_valid_until", columnList = "category,validUntil")
})
public class DocumentMetadataJpaEntity {

    @Id
    private String id;

    private String category;
    private String ownerId;

    private String tenantId;
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
    private String encryption;
    private Instant retentionUntil;
    private boolean legalHold;
    private Instant legalHoldChangedAt;
    private String legalHoldChangedBy;
    /** D12: the day the document stops being valid (inclusive), as the uploader gave it or a reviewer confirmed it; null: not tracked. */
    private LocalDate validUntil;
    /*
     * The schema is kept by hbm2ddl "update", which adds a new column with ALTER TABLE ... ADD COLUMN. A primitive maps to
     * NOT NULL, which Postgres refuses on a table that already has rows unless the column has a default; hbm2ddl then only
     * logs the failure and every read of a document fails with "column validuntilconfirmed does not exist". The default
     * lets the column be added to existing tables (old documents: not confirmed).
     */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean validUntilConfirmed;
    private String validUntilConfirmedBy;
    private Instant validUntilConfirmedAt;
    /** D12: the smallest reminder (days before expiry) already sent for {@code validUntil}; null when none. */
    private Integer expiryNoticeDays;
    private boolean deleted;
    private Instant deletedAt;
    private String deletedBy;
    @Column(nullable = false)
    private Instant createdAt;

    public static DocumentMetadataJpaEntity fromDomain(DocumentMetadata metadata) {
        DocumentMetadataJpaEntity entity = new DocumentMetadataJpaEntity();
        entity.id = metadata.getId();
        entity.category = metadata.getCategory();
        entity.ownerId = metadata.getOwnerId();
        entity.tenantId = metadata.getTenantId();
        entity.documentType = metadata.getDocumentType();
        entity.originalFileName = metadata.getOriginalFileName();
        entity.storedFileName = metadata.getStoredFileName();
        entity.objectKey = metadata.getObjectKey();
        entity.publicUrl = metadata.getPublicUrl();
        entity.provider = metadata.getProvider();
        entity.versionId = metadata.getVersionId();
        entity.checksumSha256 = metadata.getChecksumSha256();
        entity.contentType = metadata.getContentType();
        entity.sizeBytes = metadata.getSizeBytes();
        entity.status = metadata.getStatus();
        entity.uploadedBy = metadata.getUploadedBy();
        entity.scanStatus = metadata.getScanStatus();
        entity.scanMessage = metadata.getScanMessage();
        entity.encryption = metadata.getEncryption();
        entity.retentionUntil = metadata.getRetentionUntil();
        entity.legalHold = metadata.isLegalHold();
        entity.legalHoldChangedAt = metadata.getLegalHoldChangedAt();
        entity.legalHoldChangedBy = metadata.getLegalHoldChangedBy();
        entity.validUntil = metadata.getValidUntil();
        entity.validUntilConfirmed = metadata.isValidUntilConfirmed();
        entity.validUntilConfirmedBy = metadata.getValidUntilConfirmedBy();
        entity.validUntilConfirmedAt = metadata.getValidUntilConfirmedAt();
        entity.expiryNoticeDays = metadata.getExpiryNoticeDays();
        entity.deleted = metadata.isDeleted();
        entity.deletedAt = metadata.getDeletedAt();
        entity.deletedBy = metadata.getDeletedBy();
        entity.createdAt = metadata.getCreatedAt();
        return entity;
    }

    public DocumentMetadata toDomain() {
        return DocumentMetadata.builder()
                .id(id)
                .category(category)
                .ownerId(ownerId)
                .tenantId(tenantId)
                .documentType(documentType)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .objectKey(objectKey)
                .publicUrl(publicUrl)
                .provider(provider)
                .versionId(versionId)
                .checksumSha256(checksumSha256)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .status(status)
                .uploadedBy(uploadedBy)
                .scanStatus(scanStatus)
                .scanMessage(scanMessage)
                .encryption(encryption)
                .retentionUntil(retentionUntil)
                .legalHold(legalHold)
                .legalHoldChangedAt(legalHoldChangedAt)
                .legalHoldChangedBy(legalHoldChangedBy)
                .validUntil(validUntil)
                .validUntilConfirmed(validUntilConfirmed)
                .validUntilConfirmedBy(validUntilConfirmedBy)
                .validUntilConfirmedAt(validUntilConfirmedAt)
                .expiryNoticeDays(expiryNoticeDays)
                .deleted(deleted)
                .deletedAt(deletedAt)
                .deletedBy(deletedBy)
                .createdAt(createdAt)
                .build();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
