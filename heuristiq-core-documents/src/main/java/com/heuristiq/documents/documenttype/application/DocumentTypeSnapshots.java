package com.heuristiq.documents.documenttype.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeConstants;
import com.heuristiq.documents.documenttype.domain.DocumentTypeDraft;
import com.heuristiq.documents.documenttype.domain.DocumentTypeFields;
import com.heuristiq.documents.documenttype.domain.VerificationMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code before} / {@code after} snapshots exchanged with the approval service and hashes them. Pure and
 * stateless. The same map shape is used on submit and when the callback recomputes {@code beforeHash}, so a record that
 * changed in between hashes differently (stale). Null values are omitted so the hash does not depend on how a JSON
 * round trip treats them; timestamps are left out (the lock {@code version} already changes with every write).
 */
public final class DocumentTypeSnapshots {

    private DocumentTypeSnapshots() {
    }

    /** The current stored state of {@code type}, or an empty map when it does not exist yet (a create). */
    public static Map<String, Object> stateOf(DocumentType type) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (type == null) {
            return state;
        }
        state.put(DocumentTypeConstants.K_ID, type.id());
        state.put(DocumentTypeConstants.K_TENANT_ID, type.tenantId());
        state.put(DocumentTypeConstants.K_CODE, type.code());
        putCommon(state, type.category(), type.flow(), type.names(), type.allowedFormats().stream().map(Enum::name).sorted().toList(),
                type.maxFileSizeMb(), type.required(), type.expiryYears(), type.retentionDays(), type.scanRequired(),
                type.verificationRequired(), type.verificationMethod(), type.active());
        if (type.verificationProvider() != null) state.put("verificationProvider", type.verificationProvider());
        if (type.version() != null) {
            state.put(DocumentTypeConstants.K_VERSION, type.version());
        }
        return state;
    }

    /**
     * The full proposed state plus who proposes it.
     *
     * @param id              the existing id on update, null on create
     * @param expectedVersion the version the maker saw on update, null on create
     */
    public static Map<String, Object> proposedOf(String id, String tenantId, String code, DocumentTypeDraft draft,
                                                 String maker, String action, Long expectedVersion) {
        Map<String, Object> after = new LinkedHashMap<>();
        if (id != null) {
            after.put(DocumentTypeConstants.K_ID, id);
        }
        after.put(DocumentTypeConstants.K_TENANT_ID, tenantId);
        after.put(DocumentTypeConstants.K_CODE, code);
        putCommon(after, draft.category(), draft.flow(), draft.names(),
                draft.allowedFormats().stream().map(Enum::name).sorted().toList(), draft.maxFileSizeMb(), draft.required(),
                draft.expiryYears(), draft.retentionDays(), draft.scanRequired(), draft.verificationRequired(),
                draft.verificationMethod(), draft.active());
        if (draft.verificationProvider() != null) after.put("verificationProvider", draft.verificationProvider());
        after.put(DocumentTypeConstants.K_MAKER, maker);
        after.put(DocumentTypeConstants.K_ACTION, action);
        if (expectedVersion != null) {
            after.put(DocumentTypeConstants.K_EXPECTED_VERSION, expectedVersion);
        }
        return after;
    }

    /** SHA-256 hex of the canonical JSON of {@link #stateOf(DocumentType)}, as Workflow computes it for {@code before}. */
    public static String hashOf(ObjectMapper mapper, DocumentType type) {
        return CanonicalJson.sha256(mapper.valueToTree(stateOf(type)));
    }

    /** Reads the editable fields back out of an approved {@code after} snapshot. */
    public static DocumentTypeFields fieldsOf(JsonNode after) {
        Map<String, String> names = new LinkedHashMap<>();
        JsonNode namesNode = after.get(DocumentTypeConstants.K_NAMES);
        if (namesNode != null && namesNode.isObject()) {
            namesNode.fields().forEachRemaining(e -> names.put(e.getKey(), e.getValue().asText()));
        }
        List<String> formats = new ArrayList<>();
        JsonNode formatsNode = after.get(DocumentTypeConstants.K_ALLOWED_FORMATS);
        if (formatsNode != null && formatsNode.isArray()) {
            formatsNode.forEach(f -> formats.add(f.asText()));
        }
        return new DocumentTypeFields(text(after, DocumentTypeConstants.K_CODE), text(after, DocumentTypeConstants.K_CATEGORY), text(after, DocumentTypeConstants.K_FLOW), names, formats,
                integer(after, DocumentTypeConstants.K_MAX_FILE_SIZE_MB), after.has(DocumentTypeConstants.K_REQUIRED) ? after.get(DocumentTypeConstants.K_REQUIRED).asBoolean() : null,
                integer(after, DocumentTypeConstants.K_EXPIRY_YEARS), integer(after, DocumentTypeConstants.K_RETENTION_DAYS),
                after.has(DocumentTypeConstants.K_SCAN_REQUIRED) && !after.get(DocumentTypeConstants.K_SCAN_REQUIRED).isNull() ? after.get(DocumentTypeConstants.K_SCAN_REQUIRED).asBoolean() : null,
                after.has(DocumentTypeConstants.K_VERIFICATION_REQUIRED) && !after.get(DocumentTypeConstants.K_VERIFICATION_REQUIRED).isNull() ? after.get(DocumentTypeConstants.K_VERIFICATION_REQUIRED).asBoolean() : null,
                text(after, DocumentTypeConstants.K_VERIFICATION_METHOD),
                after.has(DocumentTypeConstants.K_ACTIVE) ? after.get(DocumentTypeConstants.K_ACTIVE).asBoolean() : null,
                text(after, "verificationProvider"));
    }


    /** A text field of {@code node}, or null when missing, null or blank. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.asInt();
    }

    private static void putCommon(Map<String, Object> target, String category, String flow, Map<String, String> names,
                                  List<String> formats, int maxFileSizeMb, boolean required, Integer expiryYears,
                                  Integer retentionDays, boolean scanRequired, boolean verificationRequired,
                                  VerificationMethod verificationMethod, boolean active) {
        target.put(DocumentTypeConstants.K_CATEGORY, category);
        if (flow != null) {
            target.put(DocumentTypeConstants.K_FLOW, flow);
        }
        target.put(DocumentTypeConstants.K_NAMES, new LinkedHashMap<>(names));
        target.put(DocumentTypeConstants.K_ALLOWED_FORMATS, formats);
        target.put(DocumentTypeConstants.K_MAX_FILE_SIZE_MB, maxFileSizeMb);
        target.put(DocumentTypeConstants.K_REQUIRED, required);
        if (expiryYears != null) {
            target.put(DocumentTypeConstants.K_EXPIRY_YEARS, expiryYears);
        }
        if (retentionDays != null) {
            target.put(DocumentTypeConstants.K_RETENTION_DAYS, retentionDays);
        }
        // Only the non-default value is written, so the hash of a record that predates this field (scan required)
        // is unchanged and change requests already waiting for approval do not turn stale on deploy.
        if (!scanRequired) {
            target.put(DocumentTypeConstants.K_SCAN_REQUIRED, false);
        }
        // Same for verification: only a required verification and its method are written, so a record without
        // verification (every record stored before these fields existed) hashes exactly as before, while turning
        // verification on or off, or changing the method, changes the snapshot and shows in the diff.
        if (verificationRequired) {
            target.put(DocumentTypeConstants.K_VERIFICATION_REQUIRED, true);
        }
        if (verificationMethod != null && verificationMethod != VerificationMethod.NONE) {
            target.put(DocumentTypeConstants.K_VERIFICATION_METHOD, verificationMethod.name());
        }
        target.put(DocumentTypeConstants.K_ACTIVE, active);
    }

    /** A one-line English description such as {@code Accepts PDF, JPG up to 5 MB}. */
    public static String summaryOf(Iterable<DocumentFormat> formats, int maxFileSizeMb) {
        return summaryOf(formats, maxFileSizeMb, null);
    }

    /** As {@link #summaryOf(Iterable, int)}, with {@code , retained for 365 days} appended when a retention is set. */
    public static String summaryOf(Iterable<DocumentFormat> formats, int maxFileSizeMb, Integer retentionDays) {
        List<String> keys = new ArrayList<>();
        formats.forEach(f -> keys.add(f.name()));
        String summary = "Accepts " + String.join(", ", keys) + " up to " + maxFileSizeMb + " MB";
        return retentionDays == null ? summary : summary + ", retained for " + retentionDays + " days";
    }
}
