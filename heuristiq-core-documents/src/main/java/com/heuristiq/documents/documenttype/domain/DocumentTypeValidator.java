package com.railcab.dms.documenttype.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure rules for the editable fields of a document type. Stateless; no framework types.
 */
public final class DocumentTypeValidator {

    private DocumentTypeValidator() {
    }

    /**
     * Checks and normalizes {@code fields}.
     *
     * @param fields      what the caller supplied
     * @param checkCode   true on create (the code must be valid); false on update (the code is ignored)
     * @return the normalized draft; its code is empty when {@code checkCode} is false
     * @throws DocumentTypeException (INVALID) listing every problem found
     */
    public static DocumentTypeDraft normalize(DocumentTypeFields fields, boolean checkCode) {
        List<String> problems = new ArrayList<>();
        if (fields == null) {
            throw DocumentTypeException.invalid("The document type is required");
        }

        String code = "";
        if (checkCode) {
            code = fields.code() == null ? "" : fields.code().trim().toUpperCase(Locale.ROOT);
            if (!DocumentTypeConstants.CODE_PATTERN.matcher(code).matches()) {
                problems.add("code must be 3-60 characters: capital letters, digits and underscores, starting with a letter");
            }
        }

        String category = fields.category() == null ? "" : fields.category().trim().toLowerCase(Locale.ROOT);
        if (!DocumentTypeConstants.CATEGORY_PATTERN.matcher(category).matches()) {
            problems.add("category is required (1-60 lower-case letters, digits, space, '_', '.', '-')");
        }

        String flow = fields.flow() == null || fields.flow().isBlank() ? null : fields.flow().trim().toUpperCase(Locale.ROOT);
        if (flow != null && !DocumentTypeConstants.FLOW_PATTERN.matcher(flow).matches()) {
            problems.add("flow must be UPPER_SNAKE, up to 60 characters");
        }

        Map<String, String> names = normalizeNames(fields.names(), problems);

        Set<DocumentFormat> formats = EnumSet.noneOf(DocumentFormat.class);
        if (fields.allowedFormats() == null || fields.allowedFormats().isEmpty()) {
            problems.add("allowedFormats needs at least one format");
        } else {
            for (String key : fields.allowedFormats()) {
                DocumentFormat.fromKey(key).ifPresentOrElse(formats::add,
                        () -> problems.add("Unknown format '" + key + "'"));
            }
        }

        int maxMb = 0;
        if (fields.maxFileSizeMb() == null || fields.maxFileSizeMb() < DocumentTypeConstants.MIN_FILE_SIZE_MB
                || fields.maxFileSizeMb() > DocumentTypeConstants.MAX_FILE_SIZE_MB) {
            problems.add("maxFileSizeMb must be between " + DocumentTypeConstants.MIN_FILE_SIZE_MB + " and "
                    + DocumentTypeConstants.MAX_FILE_SIZE_MB);
        } else {
            maxMb = fields.maxFileSizeMb();
        }

        Integer expiry = fields.expiryYears();
        if (expiry != null && (expiry < DocumentTypeConstants.MIN_EXPIRY_YEARS || expiry > DocumentTypeConstants.MAX_EXPIRY_YEARS)) {
            problems.add("expiryYears must be between " + DocumentTypeConstants.MIN_EXPIRY_YEARS + " and "
                    + DocumentTypeConstants.MAX_EXPIRY_YEARS + " or empty");
        }

        Integer retentionDays = fields.retentionDays();
        if (retentionDays != null && (retentionDays < DocumentTypeConstants.MIN_RETENTION_DAYS
                || retentionDays > DocumentTypeConstants.MAX_RETENTION_DAYS)) {
            problems.add("retentionDays must be between " + DocumentTypeConstants.MIN_RETENTION_DAYS + " and "
                    + DocumentTypeConstants.MAX_RETENTION_DAYS + " or empty");
        }

        boolean verificationRequired = fields.verificationRequired() == null
                ? DocumentTypeConstants.DEFAULT_VERIFICATION_REQUIRED : fields.verificationRequired();
        VerificationMethod verificationMethod = normalizeVerification(verificationRequired, fields.verificationMethod(),
                problems);
        String verificationProvider = normalizeProvider(fields.verificationProvider(), verificationMethod, problems);

        if (!problems.isEmpty()) {
            throw DocumentTypeException.invalid(String.join("; ", problems));
        }
        return new DocumentTypeDraft(code, category, flow, names, formats, maxMb,
                Boolean.TRUE.equals(fields.required()), expiry, retentionDays,
                fields.scanRequired() == null ? DocumentTypeConstants.DEFAULT_SCAN_REQUIRED : fields.scanRequired(),
                verificationRequired, verificationMethod,
                fields.active() == null || fields.active(), verificationProvider);
    }

    private static String normalizeProvider(String code, VerificationMethod method, List<String> problems) {
        if (code == null || code.isBlank()) return null; // Legacy/unconfigured types remain readable and editable.
        try {
            var provider = VerificationProviderCatalog.parse(code);
            if (!provider.supports(method)) problems.add("Verification provider " + provider.name() + " does not support " + method);
            return provider.name();
        } catch (DocumentTypeException e) { problems.add(e.getMessage()); return null; }
    }

    /**
     * The verification rule: a missing method is NONE; not required means the method must be NONE, required means it
     * must not be NONE. An unknown method is reported (and the required/method pairing is then not checked).
     */
    private static VerificationMethod normalizeVerification(boolean required, String rawMethod, List<String> problems) {
        VerificationMethod method = VerificationMethod.NONE;
        if (rawMethod != null && !rawMethod.isBlank()) {
            Optional<VerificationMethod> parsed = VerificationMethod.fromKey(rawMethod);
            if (parsed.isEmpty()) {
                problems.add(String.format(DocumentTypeConstants.MSG_UNKNOWN_VERIFICATION_METHOD, rawMethod.trim()));
                return method;
            }
            method = parsed.get();
        }
        if (!required && method != VerificationMethod.NONE) {
            problems.add(DocumentTypeConstants.MSG_VERIFICATION_METHOD_WITHOUT_REQUIRED);
        } else if (required && method == VerificationMethod.NONE) {
            problems.add(DocumentTypeConstants.MSG_VERIFICATION_METHOD_MISSING);
        }
        return method;
    }

    private static Map<String, String> normalizeNames(Map<String, String> raw, List<String> problems) {
        Map<String, String> names = new LinkedHashMap<>();
        if (raw != null) {
            for (String language : DocumentTypeConstants.LANGUAGES) {
                String value = raw.get(language);
                if (value != null && !value.isBlank()) {
                    String trimmed = value.trim();
                    if (trimmed.length() > DocumentTypeConstants.MAX_NAME_LENGTH) {
                        problems.add("names." + language + " is longer than " + DocumentTypeConstants.MAX_NAME_LENGTH
                                + " characters");
                    }
                    names.put(language, trimmed);
                }
            }
            for (String language : raw.keySet()) {
                if (!DocumentTypeConstants.LANGUAGES.contains(language)) {
                    problems.add("Unsupported language '" + language + "' in names");
                }
            }
        }
        if (!names.containsKey(DocumentTypeConstants.REQUIRED_LANGUAGE)) {
            problems.add("names.en is required");
        }
        return names;
    }
}
