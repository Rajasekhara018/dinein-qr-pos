package com.heuristiq.documents.documenttype.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Body of a create or update proposal. On update the {@code code} is ignored (it is immutable) and {@code version}
 * should be the version the caller loaded, so a concurrent change is detected.
 */
@Schema(description = "Proposed document type. Nothing is stored until a checker approves the change request.")
public record DocumentTypeRequest(
        @Schema(description = "UPPER_SNAKE code, 3-60 characters, unique in the organization (create only).", example = "MATRIMONY_PHOTO_ID")
        String code,
        @Schema(description = "Upload category this type belongs to (lower-case).", example = "matrimony-documents")
        String category,
        @Schema(description = "Optional business flow.", example = "MATRIMONY_ONBOARDING")
        String flow,
        @Schema(description = "Display name per language code (en required; hi, ta, te, kn, ml, ar optional).")
        Map<String, String> names,
        @Schema(description = "Allowed format keys: PDF, JPG, PNG, WEBP, DOC, DOCX, XLS, XLSX, CSV, TXT.")
        List<String> allowedFormats,
        @Schema(description = "Largest accepted file in MB (1-50).", example = "5")
        Integer maxFileSizeMb,
        @Schema(description = "Whether the flow requires this document.")
        Boolean required,
        @Schema(description = "Years until the document expires (1-50), or empty.")
        Integer expiryYears,
        @Schema(description = "Days an upload of this type is kept from upload time (1-36500); empty uses the service default.", nullable = true)
        Integer retentionDays,
        @Schema(description = "Whether a malware scan is mandatory for uploads of this type; uploads fail closed (503) when no scan can run. Empty means true.", defaultValue = "true")
        Boolean scanRequired,
        @Schema(description = "Whether a document of this type must be verified (configuration only: no provider is called yet). Empty means false.", defaultValue = "false")
        Boolean verificationRequired,
        @Schema(description = "How it is verified: NONE, MANUAL_REVIEW, AADHAAR_DIGILOCKER, PAN_VERIFICATION, DRIVING_LICENCE_SARATHI, VEHICLE_RC_VAHAN, BANK_PENNY_DROP. Must be NONE (or empty) when verificationRequired is false, and not NONE when it is true.", example = "MANUAL_REVIEW", defaultValue = "NONE")
        String verificationMethod,
        @Schema(description = "Whether uploads may use this type.")
        Boolean active,
        @Schema(description = "Version the caller loaded (update only), for stale detection.")
        Long version,
        @Schema(description = "One vendor code selected for this verification method. Credentials are managed separately.")
        String verificationProvider
) {
    public DocumentTypeRequest(String code, String category, String flow, Map<String, String> names, List<String> allowedFormats,
                               Integer maxFileSizeMb, Boolean required, Integer expiryYears, Integer retentionDays,
                               Boolean scanRequired, Boolean verificationRequired, String verificationMethod, Boolean active, Long version) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired,
                verificationRequired, verificationMethod, active, version, null);
    }
}
