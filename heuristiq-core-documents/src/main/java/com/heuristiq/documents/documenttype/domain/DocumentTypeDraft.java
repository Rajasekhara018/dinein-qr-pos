package com.railcab.dms.documenttype.domain;

import java.util.Map;
import java.util.Set;

/**
 * Checked and normalized fields of a document type (upper-case code and flow, lower-case trimmed category, trimmed
 * names, formats as a set, verification method never null and NONE exactly when verification is not required).
 * Produced only by {@link DocumentTypeValidator}.
 */
public record DocumentTypeDraft(
        String code,
        String category,
        String flow,
        Map<String, String> names,
        Set<DocumentFormat> allowedFormats,
        int maxFileSizeMb,
        boolean required,
        Integer expiryYears,
        Integer retentionDays,
        boolean scanRequired,
        boolean verificationRequired,
        VerificationMethod verificationMethod,
        boolean active,
        String verificationProvider
) {


    /** Source-compatible constructor for records written before provider assignments existed. */
    public DocumentTypeDraft(String code, String category, String flow, Map<String, String> names, Set<DocumentFormat> allowedFormats, int maxFileSizeMb, boolean required, Integer expiryYears, Integer retentionDays, boolean scanRequired, boolean verificationRequired, VerificationMethod verificationMethod, boolean active) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired, verificationRequired, verificationMethod, active, null);
    }

    /** A draft without verification; kept for callers that predate the verification fields. */
    public DocumentTypeDraft(String code, String category, String flow, Map<String, String> names,
                             Set<DocumentFormat> allowedFormats, int maxFileSizeMb, boolean required, Integer expiryYears,
                             Integer retentionDays, boolean scanRequired, boolean active) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired,
                DocumentTypeConstants.DEFAULT_VERIFICATION_REQUIRED, VerificationMethod.NONE, active);
    }
}
