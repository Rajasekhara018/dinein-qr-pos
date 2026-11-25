package com.heuristiq.identity.pos.domain;

/**
 * A refused POS read or change. The message is a translatable message key; {@link #field()} names the offending field (for
 * example {@code address.postalCode}) when there is one. The kind tells the web layer which answer to give.
 */
public class PosException extends RuntimeException {

    /** Why it was refused. */
    public enum Kind {
        /** The request is invalid (400). */
        INVALID,
        /** The request clashes with the current state, e.g. a duplicate or stale version (409). */
        CONFLICT,
        /** The thing does not exist, or the caller may not see it (404). */
        NOT_FOUND
    }

    private final Kind kind;
    private final String field;

    private PosException(Kind kind, String messageKey, String field) {
        super(messageKey);
        this.kind = kind;
        this.field = field;
    }

    public static PosException invalid(String messageKey, String field) {
        return new PosException(Kind.INVALID, messageKey, field);
    }

    public static PosException conflict(String messageKey, String field) {
        return new PosException(Kind.CONFLICT, messageKey, field);
    }

    public static PosException notFound(String messageKey) {
        return new PosException(Kind.NOT_FOUND, messageKey, null);
    }

    public Kind kind() {
        return kind;
    }

    /** The offending field's path, or null. */
    public String field() {
        return field;
    }
}
