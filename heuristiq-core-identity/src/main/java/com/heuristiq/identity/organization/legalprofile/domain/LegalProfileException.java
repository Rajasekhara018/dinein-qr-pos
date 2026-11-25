package com.heuristiq.identity.organization.legalprofile.domain;

/**
 * A refused legal-profile read or change. The message is a translatable message key; {@link #field()} names the offending
 * field (for example {@code addresses[0].postalCode}) when there is one. The kind tells the web layer which answer to give.
 */
public class LegalProfileException extends RuntimeException {

    /** Why it was refused. */
    public enum Kind {
        /** The profile sent is invalid (400). */
        INVALID,
        /** The profile was changed since the caller read it (409). */
        CONFLICT,
        /** The organization does not exist, or the caller may not see it (404). */
        NOT_FOUND
    }

    private final Kind kind;
    private final String field;

    private LegalProfileException(Kind kind, String messageKey, String field) {
        super(messageKey);
        this.kind = kind;
        this.field = field;
    }

    public static LegalProfileException invalid(String messageKey, String field) {
        return new LegalProfileException(Kind.INVALID, messageKey, field);
    }

    public static LegalProfileException staleVersion() {
        return new LegalProfileException(Kind.CONFLICT, LegalProfileConstants.ERROR_STALE_VERSION, LegalProfileConstants.FIELD_VERSION);
    }

    public static LegalProfileException organizationNotFound() {
        return new LegalProfileException(Kind.NOT_FOUND, LegalProfileConstants.ERROR_ORGANIZATION_NOT_FOUND, null);
    }

    public Kind kind() {
        return kind;
    }

    /** The offending field's path, or null. */
    public String field() {
        return field;
    }
}
