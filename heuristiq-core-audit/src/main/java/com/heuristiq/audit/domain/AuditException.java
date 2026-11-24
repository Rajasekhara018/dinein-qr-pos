package com.railcab.audit.domain;

/**
 * A business-rule failure of the audit service. It carries a {@link Kind} instead of an HTTP status so the domain
 * and application layers stay free of web types; the web adapter ({@code GlobalExceptionHandler}) maps the kind
 * to a status code.
 */
public class AuditException extends RuntimeException {

    /** Category of the failure. */
    public enum Kind {
        /** The request is not acceptable (bad filter, empty batch). */
        INVALID,
        /** The request is syntactically fine but too large (batch over the limit). */
        TOO_LARGE,
        /** The caller is not authenticated. */
        UNAUTHENTICATED,
        /** The requested record does not exist. */
        NOT_FOUND
    }

    private final Kind kind;

    /**
     * Creates an {@link Kind#INVALID} failure.
     *
     * @param message what is wrong, safe to show to the caller
     */
    public AuditException(String message) {
        this(message, Kind.INVALID);
    }

    /**
     * Creates a failure of the given kind.
     *
     * @param message what is wrong, safe to show to the caller
     * @param kind    the category
     */
    public AuditException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    /** @return a {@link Kind#NOT_FOUND} failure */
    public static AuditException notFound(String message) {
        return new AuditException(message, Kind.NOT_FOUND);
    }

    /** @return the category of this failure */
    public Kind getKind() {
        return kind;
    }
}
