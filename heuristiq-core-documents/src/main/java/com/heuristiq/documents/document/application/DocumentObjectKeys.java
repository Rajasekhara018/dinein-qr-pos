package com.heuristiq.documents.document.application;

import com.heuristiq.documents.document.audit.DocumentReasonCodes;
import com.heuristiq.documents.exception.DmsException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Builds the storage object key of a new upload and makes every part of it traversal-safe. Stateless.
 *
 * <p>New key shape: {@code <tenant>/<category>/<documentType|general>/<ownerId>/<storedFileName>}. Documents stored
 * before this shape (<code>&lt;category&gt;/&lt;ownerId&gt;/&lt;storedFileName&gt;</code>) are never rewritten: every
 * read, download, presign and delete uses the {@code objectKey} saved in the document's metadata, not a rebuilt key.
 */
public final class DocumentObjectKeys {

    private static final String SEPARATOR = "/";
    private static final String UNSAFE_CHARACTERS = "[^A-Za-z0-9._-]";
    private static final String DEFAULT_FILE_NAME = "document";

    private DocumentObjectKeys() {
    }

    /**
     * Turns user- or token-supplied text into one safe path segment: separators of either kind, unicode and any other
     * character outside {@code A-Za-z0-9._-} become {@code _}.
     *
     * @throws DmsException 400 when the value is empty, is blank, is only dots, contains {@code ..}, or is longer than
     *                      {@link DocumentConstants#MAX_SEGMENT_LENGTH}
     */
    public static String safePathPart(String value) {
        if (!StringUtils.hasText(value)) {
            throw DmsException.badRequest("Invalid document path", DocumentReasonCodes.PATH_INVALID);
        }
        String cleaned = StringUtils.cleanPath(value).replaceAll(UNSAFE_CHARACTERS, "_");
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.chars().allMatch(c -> c == '.')
                || cleaned.length() > DocumentConstants.MAX_SEGMENT_LENGTH) {
            throw DmsException.badRequest("Invalid document path", DocumentReasonCodes.PATH_INVALID);
        }
        return cleaned;
    }

    /** The organization segment: the sanitised tenant id, or {@link DocumentConstants#NO_ORGANIZATION_SEGMENT} when there is none. */
    public static String tenantSegment(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return DocumentConstants.NO_ORGANIZATION_SEGMENT;
        }
        String segment = safePathPart(tenantId);
        if (DocumentConstants.NO_ORGANIZATION_SEGMENT.equals(segment)) {
            throw DmsException.badRequest("Invalid document path", DocumentReasonCodes.PATH_INVALID);
        }
        return segment;
    }

    /** The document-type segment: the sanitised type code, or {@link DocumentConstants#GENERAL_TYPE_SEGMENT} when none. */
    public static String documentTypeSegment(String documentTypeCode) {
        return StringUtils.hasText(documentTypeCode) ? safePathPart(documentTypeCode) : DocumentConstants.GENERAL_TYPE_SEGMENT;
    }

    /** A safe, lower-case stored file name derived from the uploaded name ({@code document} when nothing usable). */
    public static String safeFileName(String originalName) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(originalName) ? originalName : DEFAULT_FILE_NAME);
        String safe = cleaned.replaceAll(UNSAFE_CHARACTERS, "_");
        if (!StringUtils.hasText(safe) || safe.contains("..") || safe.chars().allMatch(c -> c == '.')) {
            return DEFAULT_FILE_NAME;
        }
        if (safe.length() > DocumentConstants.MAX_FILE_NAME_LENGTH) {
            safe = safe.substring(safe.length() - DocumentConstants.MAX_FILE_NAME_LENGTH);
        }
        return safe.toLowerCase(Locale.ROOT);
    }

    /**
     * The key for a new upload. Every part must already be a safe segment (use the methods above).
     *
     * @param tenantSegment       from {@link #tenantSegment}
     * @param category            sanitised category
     * @param documentTypeSegment from {@link #documentTypeSegment}
     * @param ownerId             sanitised owner id
     * @param storedFileName      sanitised stored file name
     */
    public static String build(String tenantSegment, String category, String documentTypeSegment, String ownerId,
                               String storedFileName) {
        return String.join(SEPARATOR, tenantSegment, category, documentTypeSegment, ownerId, storedFileName);
    }
}
