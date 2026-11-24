package com.railcab.dms.documenttype.domain;

import java.util.Collection;
import java.util.Map;

/**
 * The editable fields of a document type as the caller supplied them (not yet checked). Boxed types so a missing
 * value can be told from zero/false.
 *
 * @param code           UPPER_SNAKE code (ignored on update: the code is immutable)
 * @param category       upload category
 * @param flow           optional flow
 * @param names          name per language code
 * @param allowedFormats format keys
 * @param maxFileSizeMb  size limit in MB
 * @param required       whether the flow requires the document
 * @param expiryYears    optional expiry in years
 * @param retentionDays  optional retention in days (1-36500); null means the service default
 * @param scanRequired   whether a malware scan is mandatory; null means true
 * @param verificationRequired whether the document must be verified; null means false
 * @param verificationMethod   key of a {@link VerificationMethod}; null or blank means NONE
 * @param active         whether uploads may use it
 */
public record DocumentTypeFields(
        String code,
        String category,
        String flow,
        Map<String, String> names,
        Collection<String> allowedFormats,
        Integer maxFileSizeMb,
        Boolean required,
        Integer expiryYears,
        Integer retentionDays,
        Boolean scanRequired,
        Boolean verificationRequired,
        String verificationMethod,
        Boolean active,
        String verificationProvider
) {


    /** Source-compatible constructor for records written before provider assignments existed. */
    public DocumentTypeFields(String code, String category, String flow, Map<String, String> names, Collection<String> allowedFormats, Integer maxFileSizeMb, Boolean required, Integer expiryYears, Integer retentionDays, Boolean scanRequired, Boolean verificationRequired, String verificationMethod, Boolean active) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired, verificationRequired, verificationMethod, active, null);
    }

    /** Fields without verification settings (no verification); kept for callers that predate them. */
    public DocumentTypeFields(String code, String category, String flow, Map<String, String> names,
                              Collection<String> allowedFormats, Integer maxFileSizeMb, Boolean required,
                              Integer expiryYears, Integer retentionDays, Boolean scanRequired, Boolean active) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, retentionDays, scanRequired,
                null, null, active);
    }

    /** Fields without retention and scan settings (retention default, scan required); kept for callers that predate them. */
    public DocumentTypeFields(String code, String category, String flow, Map<String, String> names,
                              Collection<String> allowedFormats, Integer maxFileSizeMb, Boolean required,
                              Integer expiryYears, Boolean active) {
        this(code, category, flow, names, allowedFormats, maxFileSizeMb, required, expiryYears, null, null, null, null, active);
    }
}
