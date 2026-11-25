package com.heuristiq.identity.biometric.domain;

/** A refused biometric enrolment or sign-in; the kind tells the web layer which status and error code to answer. */
public class BiometricException extends RuntimeException {

    /** Why the call was refused. */
    public enum Kind {
        /** The request itself is unusable (400). */
        INVALID_REQUEST,
        /** Enrolment needs a recent interactive sign-in or the current password (401 REAUTH_REQUIRED). */
        REAUTH_REQUIRED,
        /** Any challenge, credential or signature problem of a sign-in (401 BIOMETRIC_SIGNIN_INVALID). */
        SIGNIN_INVALID,
        /** The installation's credential was revoked or expired (401 BIOMETRIC_CREDENTIAL_REVOKED). */
        CREDENTIAL_REVOKED,
        /** Too many failed sign-ins from the installation (429 TOO_MANY_ATTEMPTS). */
        TOO_MANY_ATTEMPTS,
        /** No such device of the caller (404). */
        NOT_FOUND,
        /** A concurrent enrolment of the same installation won (409). */
        CONFLICT
    }

    private final Kind kind;

    private BiometricException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static BiometricException invalidRequest(String message) {
        return new BiometricException(Kind.INVALID_REQUEST, message);
    }

    public static BiometricException reauthRequired() {
        return new BiometricException(Kind.REAUTH_REQUIRED, BiometricConstants.MESSAGE_REAUTH_REQUIRED);
    }

    public static BiometricException signInInvalid() {
        return new BiometricException(Kind.SIGNIN_INVALID, BiometricConstants.MESSAGE_SIGNIN_INVALID);
    }

    public static BiometricException credentialRevoked() {
        return new BiometricException(Kind.CREDENTIAL_REVOKED, BiometricConstants.MESSAGE_CREDENTIAL_REVOKED);
    }

    public static BiometricException tooManyAttempts() {
        return new BiometricException(Kind.TOO_MANY_ATTEMPTS, BiometricConstants.MESSAGE_TOO_MANY_ATTEMPTS);
    }

    public static BiometricException notFound() {
        return new BiometricException(Kind.NOT_FOUND, BiometricConstants.MESSAGE_DEVICE_NOT_FOUND);
    }

    public static BiometricException conflict() {
        return new BiometricException(Kind.CONFLICT, BiometricConstants.MESSAGE_ENROL_CONFLICT);
    }

    public Kind kind() {
        return kind;
    }
}
