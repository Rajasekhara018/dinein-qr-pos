package com.railcab.dms.lifecycle.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Everything recorded about one document, deleted or not. */
@Schema(description = "Full lifecycle detail of one document.")
public record LifecycleDocumentDetail(
        String id,
        String originalFileName,
        String category,
        String documentType,
        String ownerId,
        String contentType,
        long sizeBytes,
        String status,
        String scanStatus,
        boolean legalHold,
        boolean deleted,
        Instant retentionUntil,
        Instant createdAt,
        String storedFileName,
        String objectKey,
        String provider,
        String versionId,
        String checksumSha256,
        @Schema(description = "Encryption at rest applied: NONE, SSE_S3, SSE_KMS or PROVIDER_MANAGED; null for older documents.")
        String encryption,
        String scanMessage,
        String uploadedBy,
        Instant deletedAt,
        String deletedBy,
        Instant legalHoldChangedAt,
        String legalHoldChangedBy,
        String tenantId
) {
}
