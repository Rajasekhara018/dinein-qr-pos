package com.heuristiq.documents.document.dto;

import com.heuristiq.documents.document.application.DocumentConstants;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common "stored document" shape shared across services (UAM's {@code helper.RUpload} is the
 * original; AdminAPI/BookingsAPI keep their own local copies per this repo's no-shared-contracts
 * convention). Mongo-only here, so no JPA {@code @Embeddable}/{@code @Enumerated} -- Spring Data
 * Mongo serializes enums as strings natively.
 */
@Schema(description = "Common stored-document shape shared across services; DMS maps its own DocumentResponse onto this for cross-service consumers.")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RUpload {

    @Schema(description = "Original file name.", example = "invoice-2026-07.pdf")
    private String name;
    @Schema(description = "Content type of the file.", example = "application/pdf")
    private String fileType;
    @Schema(description = "Size of the file in bytes.", example = "204800")
    private long size;
    @Schema(description = "Public URL of the stored object, if the storage provider exposes one.")
    private String location;
    @Schema(description = "Display title; mirrors the original file name.", example = "invoice-2026-07.pdf")
    private String title;
    @Schema(description = "Logical category the document is stored under.", example = "invoices")
    private String prefixFolder;
    @Schema(description = "Base64-encoded file contents. Always null when produced from a metadata read " +
            "(populating it would mean base64-encoding file bytes on every read).")
    private String base64EncodedData;
    @Schema(description = "Owner id the document belongs to.", example = "user-12345")
    private String modelKey;
    @Schema(description = "Derived retention policy bucket, based on legal-hold status and remaining retention window.")
    private FileRetentionPolicyEnum retentionPolicy;
    @Schema(description = "Storage provider's version identifier for the stored object, if versioning is supported.")
    private String versionId;

    /** Maps DMS's own DocumentResponse onto the common RUpload shape. base64EncodedData is left
     *  null here -- populating it would mean base64-encoding file bytes on every metadata read. */
    public static RUpload from(DocumentResponse response) {
        RUpload upload = new RUpload();
        upload.setName(response.originalFileName());
        upload.setFileType(response.contentType());
        upload.setSize(response.sizeBytes());
        upload.setLocation(response.publicUrl());
        upload.setTitle(response.originalFileName());
        upload.setPrefixFolder(response.category());
        upload.setModelKey(response.ownerId());
        upload.setRetentionPolicy(retentionPolicyOf(response));
        upload.setVersionId(response.versionId());
        return upload;
    }

    private static FileRetentionPolicyEnum retentionPolicyOf(DocumentResponse response) {
        if (response.legalHold()) {
            return FileRetentionPolicyEnum.MERCHANT_DOCUMENTS;
        }
        Instant retentionUntil = response.retentionUntil();
        if (retentionUntil == null) {
            return FileRetentionPolicyEnum.RETAIN_EVER;
        }
        long days = Duration.between(Instant.now(), retentionUntil).toDays();
        if (days <= DocumentConstants.RETAIN_SHORT_MAX_DAYS) return FileRetentionPolicyEnum.RETAIN_SHORT;
        if (days <= DocumentConstants.RETAIN_NORMAL_MAX_DAYS) return FileRetentionPolicyEnum.RETAIN_NORMAL;
        return FileRetentionPolicyEnum.RETAIN_LONG;
    }
}
