package com.heuristiq.documents.documenttype.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/** Builds the document type every organization gets automatically. Pure: no I/O. */
public final class DefaultDocumentTypes {

    private DefaultDocumentTypes() {
    }

    /**
     * The default type of one organization: every supported format, a malware scan, not required, active, created by the
     * system itself (it never went through a change request).
     *
     * @param id            new UUID string
     * @param maxFileSizeMb largest accepted file, within {@link DocumentTypeConstants#MIN_FILE_SIZE_MB} and
     *                      {@link DocumentTypeConstants#MAX_FILE_SIZE_MB}
     * @param retentionDays days uploads of the type are kept, or null for the service default
     */
    public static DocumentType create(String tenantId, String id, Instant now, int maxFileSizeMb, Integer retentionDays) {
        return new DocumentType(id, tenantId, DocumentTypeConstants.DEFAULT_TYPE_CODE,
                DocumentTypeConstants.DEFAULT_TYPE_CATEGORY, null, DocumentTypeConstants.DEFAULT_TYPE_NAMES,
                EnumSet.allOf(DocumentFormat.class), maxFileSizeMb, false, null, retentionDays,
                DocumentTypeConstants.DEFAULT_SCAN_REQUIRED, true, null, now, now, DocumentTypeConstants.SYSTEM_ACTOR,
                DocumentTypeConstants.SYSTEM_ACTOR, null, List.of());
    }

    /** True when {@code code} (any case) is the code of the default type. */
    public static boolean isDefaultCode(String code) {
        return code != null && DocumentTypeConstants.DEFAULT_TYPE_CODE.equalsIgnoreCase(code.trim());
    }
}
