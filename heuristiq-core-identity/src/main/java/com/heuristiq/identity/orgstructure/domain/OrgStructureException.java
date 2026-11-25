package com.heuristiq.identity.orgstructure.domain;

/** Business-rule failure in the organization structure; the inbound adapter maps {@link Kind} to HTTP. */
public class OrgStructureException extends RuntimeException {
    public enum Kind { INVALID, NOT_FOUND, CONFLICT, FORBIDDEN }

    private final Kind kind;

    public OrgStructureException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static OrgStructureException invalid(String message) {
        return new OrgStructureException(Kind.INVALID, message);
    }

    public static OrgStructureException notFound(String message) {
        return new OrgStructureException(Kind.NOT_FOUND, message);
    }

    public static OrgStructureException conflict(String message) {
        return new OrgStructureException(Kind.CONFLICT, message);
    }

    public static OrgStructureException forbidden(String message) {
        return new OrgStructureException(Kind.FORBIDDEN, message);
    }
}
