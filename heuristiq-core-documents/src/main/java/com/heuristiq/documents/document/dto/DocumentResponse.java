package com.heuristiq.documents.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Persisted metadata for a single document.")
public record DocumentResponse(
        @Schema(description = "Document metadata id.", example = "6620f1a4b7c9d2a1e4f5a6b7") String id,
        @Schema(description = "Logical document category.", example = "invoices") String category,
        @Schema(description = "Identifier of the document's owner.", example = "user-12345") String ownerId,
        @Schema(description = "Original file name as uploaded by the client.", example = "invoice-2026-07.pdf") String originalFileName,
        @Schema(description = "Sanitized, uniquely-prefixed file name used in storage.", example = "a1b2c3d4_invoice-2026-07.pdf") String storedFileName,
        @Schema(description = "Public URL of the stored object, if the storage provider exposes one.") String publicUrl,
        @Schema(description = "Storage provider that holds the object (e.g. s3, azure, gcp, minio, local).", example = "s3") String provider,
        @Schema(description = "Storage provider's version identifier for the stored object, if versioning is supported.") String versionId,
        @Schema(description = "SHA-256 checksum (hex) computed at upload time.", example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") String checksumSha256,
        @Schema(description = "Content type detected/declared for the stored file.", example = "application/pdf") String contentType,
        @Schema(description = "Size of the stored file in bytes.", example = "204800") long sizeBytes,
        @Schema(description = "Lifecycle status of the document.", example = "UPLOADED") String status,
        @Schema(description = "Result of the malware scan performed at upload time.", example = "CLEAN") String scanStatus,
        @Schema(description = "Human-readable detail for the scan result.") String scanMessage,
        @Schema(description = "Timestamp after which the document is eligible for retention cleanup, if applicable.") Instant retentionUntil,
        @Schema(description = "Whether the document is currently under legal hold (blocks deletion regardless of retention).") boolean legalHold,
        @Schema(description = "Timestamp the document was uploaded.") Instant createdAt,
        @Schema(description = "Storage object key the document is served at (GET /api/v1/dms/documents/{objectKey}). Its shape is <organization>/<category>/<documentType|general>/<ownerId>/<storedFileName> for new uploads and <category>/<ownerId>/<storedFileName> for older ones, so callers must use this value instead of building a key.", example = "default/matrimony-media/general/user-12345/a1b2c3d4_file.png") String objectKey,
        @Schema(description = "Last day the document is valid (ISO date), as the uploader gave it or a reviewer confirmed it; null when not tracked.", example = "2027-03-31") LocalDate validUntil,
        @Schema(description = "True once a reviewer confirmed or corrected validUntil.") boolean validUntilConfirmed
) {
}
