package com.heuristiq.documents.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A time-limited download URL for a document -- either a storage-provider pre-signed URL or a signed app-level token URL.")
public record DocumentDownloadUrlResponse(
        @Schema(description = "The download URL. Either a storage-provider pre-signed URL or /documents/public/{token}.",
                example = "/documents/public/eyJhbGciOi...") String url,
        @Schema(description = "When the URL expires and can no longer be used.") Instant expiresAt
) {
}
