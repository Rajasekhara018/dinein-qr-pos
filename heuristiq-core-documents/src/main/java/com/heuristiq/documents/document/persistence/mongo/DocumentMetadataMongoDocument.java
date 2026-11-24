package com.heuristiq.documents.document.persistence.mongo;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "dms_documents")
@CompoundIndexes({
        @CompoundIndex(name = "idx_tenant_created", def = "{'tenantId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_owner_created", def = "{'tenantId': 1, 'ownerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_category_created", def = "{'tenantId': 1, 'category': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_type_created", def = "{'tenantId': 1, 'documentType': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_status_created", def = "{'tenantId': 1, 'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_scan_created", def = "{'tenantId': 1, 'scanStatus': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_deleted_created", def = "{'tenantId': 1, 'deleted': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_tenant_filename", def = "{'tenantId': 1, 'originalFileName': 1}"),
        @CompoundIndex(name = "idx_tenant_checksum", def = "{'tenantId': 1, 'checksumSha256': 1}"),
        @CompoundIndex(name = "idx_category_valid_until", def = "{'category': 1, 'validUntil': 1}")
})
public class DocumentMetadataMongoDocument {

    @Id
    private String id;

    @Indexed
    private String category;

    @Indexed
    private String ownerId;

    @Indexed
    private String tenantId;

    private String documentType;
    private String originalFileName;
    private String storedFileName;
    @Indexed
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
    private boolean validUntilConfirmed;
    private String validUntilConfirmedBy;
    private Instant validUntilConfirmedAt;
    /** D12: the smallest reminder (days before expiry) already sent for {@code validUntil}; null when none. */
    private Integer expiryNoticeDays;

    @Indexed
    private boolean deleted;
    private Instant deletedAt;
    private String deletedBy;

    private Instant createdAt;

    public static DocumentMetadataMongoDocument fromDomain(DocumentMetadata metadata) {
        DocumentMetadataMongoDocument document = new DocumentMetadataMongoDocument();
        document.id = metadata.getId();
        document.category = metadata.getCategory();
        document.ownerId = metadata.getOwnerId();
        document.tenantId = metadata.getTenantId();
        document.documentType = metadata.getDocumentType();
        document.originalFileName = metadata.getOriginalFileName();
        document.storedFileName = metadata.getStoredFileName();
        document.objectKey = metadata.getObjectKey();
        document.publicUrl = metadata.getPublicUrl();
        document.provider = metadata.getProvider();
        document.versionId = metadata.getVersionId();
        document.checksumSha256 = metadata.getChecksumSha256();
        document.contentType = metadata.getContentType();
        document.sizeBytes = metadata.getSizeBytes();
        document.status = metadata.getStatus();
        document.uploadedBy = metadata.getUploadedBy();
        document.scanStatus = metadata.getScanStatus();
        document.scanMessage = metadata.getScanMessage();
        document.encryption = metadata.getEncryption();
        document.retentionUntil = metadata.getRetentionUntil();
        document.legalHold = metadata.isLegalHold();
        document.legalHoldChangedAt = metadata.getLegalHoldChangedAt();
        document.legalHoldChangedBy = metadata.getLegalHoldChangedBy();
        document.validUntil = metadata.getValidUntil();
        document.validUntilConfirmed = metadata.isValidUntilConfirmed();
        document.validUntilConfirmedBy = metadata.getValidUntilConfirmedBy();
        document.validUntilConfirmedAt = metadata.getValidUntilConfirmedAt();
        document.expiryNoticeDays = metadata.getExpiryNoticeDays();
        document.deleted = metadata.isDeleted();
        document.deletedAt = metadata.getDeletedAt();
        document.deletedBy = metadata.getDeletedBy();
        document.createdAt = metadata.getCreatedAt();
        return document;
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
