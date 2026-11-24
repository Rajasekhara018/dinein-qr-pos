package com.heuristiq.documents.lifecycle.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One document in the lifecycle search results. */
@Schema(description = "A document in the troubleshooting search.")
public record LifecycleDocumentRow(
        String id,
        String originalFileName,
        String category,
        @Schema(description = "Document type code named at upload; null when none was named or for older documents.")
        String documentType,
        String ownerId,
        String contentType,
        long sizeBytes,
        String status,
        String scanStatus,
        boolean legalHold,
        boolean deleted,
        Instant retentionUntil,
        Instant createdAt
) {
}
