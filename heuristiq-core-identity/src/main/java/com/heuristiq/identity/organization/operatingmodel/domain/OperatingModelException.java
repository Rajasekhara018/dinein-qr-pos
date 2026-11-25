package com.heuristiq.identity.organization.operatingmodel.domain;

/** A refused operating-model change; the kind tells the web layer which answer to give. */
public class OperatingModelException extends RuntimeException {

    /** Why the change was refused. */
    public enum Kind {
        /** The request itself is incomplete or wrong (400). */
        INVALID,
        /** The request clashes with the schedule as it stands (409). */
        CONFLICT
    }

    private final Kind kind;

    private OperatingModelException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static OperatingModelException invalid(String message) {
        return new OperatingModelException(Kind.INVALID, message);
    }

    public static OperatingModelException conflict(String message) {
        return new OperatingModelException(Kind.CONFLICT, message);
    }

    public Kind kind() {
        return kind;
    }
}
