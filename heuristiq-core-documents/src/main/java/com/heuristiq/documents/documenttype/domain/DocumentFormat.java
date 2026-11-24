package com.heuristiq.documents.documenttype.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The closed set of file formats an organization can allow for a document type. Each key maps to exactly the
 * extensions and content types DMS already accepts in {@code railcab.dms.validation.*}. No executable or script
 * format is, or may be, added here: the {@code railcab.dms.scan.blocked-*} lists stay in force on top of this.
 */
public enum DocumentFormat {
    PDF(List.of(".pdf"), List.of("application/pdf")),
    JPG(List.of(".jpg", ".jpeg"), List.of("image/jpeg")),
    PNG(List.of(".png"), List.of("image/png")),
    WEBP(List.of(".webp"), List.of("image/webp")),
    DOC(List.of(".doc"), List.of("application/msword")),
    DOCX(List.of(".docx"), List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    XLS(List.of(".xls"), List.of("application/vnd.ms-excel")),
    XLSX(List.of(".xlsx"), List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    CSV(List.of(".csv"), List.of("text/csv")),
    TXT(List.of(".txt"), List.of("text/plain"));

    private final List<String> extensions;
    private final List<String> contentTypes;

    DocumentFormat(List<String> extensions, List<String> contentTypes) {
        this.extensions = extensions;
        this.contentTypes = contentTypes;
    }

    /** Lower-case extensions including the dot, e.g. {@code .jpg}. */
    public List<String> extensions() {
        return extensions;
    }

    /** Lower-case MIME types accepted for this format. */
    public List<String> contentTypes() {
        return contentTypes;
    }

    /**
     * Parses a format key.
     *
     * @param key the key, case-insensitive
     * @return the format, or empty when the key is unknown
     */
    public static Optional<DocumentFormat> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.name().equals(normalized)).findFirst();
    }
}
