package com.railcab.dms.documenttype.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A stored document type of the caller's organization. */
@Schema(description = "A document type of the caller's organization.")
public record DocumentTypeResponse(
        String id,
        String tenantId,
        String code,
        String category,
        String flow,
        Map<String, String> names,
        List<String> allowedFormats,
        int maxFileSizeMb,
        boolean required,
        Integer expiryYears,
        @Schema(description = "Days an upload is kept, or null when the service default applies.", nullable = true)
        Integer retentionDays,
        @Schema(description = "Whether a malware scan is mandatory (default true).")
        boolean scanRequired,
        @Schema(description = "Whether a document of this type must be verified (default false).")
        boolean verificationRequired,
        @Schema(description = "Verification method; NONE when verification is not required.", example = "NONE")
        String verificationMethod,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        String lastChangeRequestId,
        @Schema(description = "Ready-made English line, e.g. 'Accepts PDF, JPG up to 5 MB'.")
        String summary,
        String verificationProvider
) {
}
