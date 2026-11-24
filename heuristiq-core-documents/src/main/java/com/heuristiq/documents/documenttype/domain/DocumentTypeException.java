package com.railcab.dms.documenttype.domain;

/**
 * Base of the document-type failures. The web layer maps {@link Kind} to an HTTP status; the domain itself knows
 * nothing about HTTP.
 */
public class DocumentTypeException extends RuntimeException {

    /** What went wrong, from the caller's point of view. */
    public enum Kind {
        /** The input is not acceptable (HTTP 400). */
        INVALID,
        /** The caller is not allowed to do this (HTTP 403). */
        FORBIDDEN,
        /** There is no such record in the caller's organization (HTTP 404). */
        NOT_FOUND,
        /** The record changed meanwhile, or the code is taken (HTTP 409). */
        CONFLICT,
        /** The approval service could not be reached; nothing was changed (HTTP 503). */
        APPROVAL_UNAVAILABLE
    }

    private final Kind kind;

    protected DocumentTypeException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** 400: the request is not valid. */
    public static DocumentTypeException invalid(String message) {
        return new DocumentTypeException(Kind.INVALID, message);
    }

    /** 403: for example the maker approving their own change. */
    public static DocumentTypeException forbidden(String message) {
        return new DocumentTypeException(Kind.FORBIDDEN, message);
    }

    /** 404: unknown in this organization (also used for another organization's record). */
    public static DocumentTypeException notFound(String message) {
        return new DocumentTypeException(Kind.NOT_FOUND, message);
    }

    /** 409: stale change or duplicate code. */
    public static DocumentTypeException conflict(String message) {
        return new DocumentTypeException(Kind.CONFLICT, message);
    }

    /** 503: Workflow is unavailable or the callback address is not configured; nothing was changed. */
    public static DocumentTypeException approvalUnavailable(String message) {
        return new DocumentTypeException(Kind.APPROVAL_UNAVAILABLE, message);
    }
}
