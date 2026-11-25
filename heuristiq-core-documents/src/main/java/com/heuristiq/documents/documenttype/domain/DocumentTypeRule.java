package com.heuristiq.documents.documenttype.domain;

import java.util.List;

/**
 * The effective upload rule of an active document type.
 *
 * @param documentTypeCode  the type's code
 * @param category          the category it belongs to
 * @param maxFileSizeMb     size limit in megabytes
 * @param allowedFormats    format keys
 * @param allowedExtensions lower-case extensions with dot (blocked executable types already removed)
 * @param allowedContentTypes lower-case MIME types (blocked ones already removed)
 * @param retentionDays     days to keep an upload of this type, or null for the service default
 * @param scanRequired      whether a malware scan is mandatory (uploads fail closed when none can run)
 * @param verificationRequired whether a document of the type must be verified (configuration only for now)
 * @param verificationMethod   how it is verified; NONE when not required
 */
public record DocumentTypeRule(
        String documentTypeCode,
        String category,
        int maxFileSizeMb,
        List<String> allowedFormats,
        List<String> allowedExtensions,
        List<String> allowedContentTypes,
        Integer retentionDays,
        boolean scanRequired,
        boolean verificationRequired,
        VerificationMethod verificationMethod,
        String verificationProvider
) {


    /** Source-compatible constructor for records written before provider assignments existed. */
    public DocumentTypeRule(String documentTypeCode, String category, int maxFileSizeMb, List<String> allowedFormats, List<String> allowedExtensions, List<String> allowedContentTypes, Integer retentionDays, boolean scanRequired, boolean verificationRequired, VerificationMethod verificationMethod) {
        this(documentTypeCode, category, maxFileSizeMb, allowedFormats, allowedExtensions, allowedContentTypes, retentionDays, scanRequired, verificationRequired, verificationMethod, null);
    }

    /** A missing method reads as {@link VerificationMethod#NONE}. */
    public DocumentTypeRule {
        verificationMethod = verificationMethod == null ? VerificationMethod.NONE : verificationMethod;
    }

    /** A rule without verification; kept for callers that predate the verification fields. */
    public DocumentTypeRule(String documentTypeCode, String category, int maxFileSizeMb, List<String> allowedFormats,
                            List<String> allowedExtensions, List<String> allowedContentTypes, Integer retentionDays,
                            boolean scanRequired) {
        this(documentTypeCode, category, maxFileSizeMb, allowedFormats, allowedExtensions, allowedContentTypes,
                retentionDays, scanRequired, DocumentTypeConstants.DEFAULT_VERIFICATION_REQUIRED, VerificationMethod.NONE);
    }

    /** The size limit in bytes. */
    public long maxFileSizeBytes() {
        return maxFileSizeMb * DocumentTypeConstants.BYTES_PER_MB;
    }
}
