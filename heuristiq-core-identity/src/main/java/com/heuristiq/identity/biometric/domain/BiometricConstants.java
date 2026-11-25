package com.heuristiq.identity.biometric.domain;

import java.util.regex.Pattern;

/**
 * Fixed values of "sign in with fingerprint / Face ID" (device-key sign-in): the error codes the apps branch on (never
 * change them), the messages sent with them, input limits, key rules and the audit names. Durations and limits that may
 * differ per deployment live in {@code BiometricProperties} ({@code railcab.uam.biometric.*}), not here.
 */
public final class BiometricConstants {

    /** 401: enrolling needs a recent interactive sign-in or the current password. */
    public static final String ERROR_REAUTH_REQUIRED = "REAUTH_REQUIRED";

    /** 401: every challenge, credential or signature problem of a biometric sign-in (deliberately no detail). */
    public static final String ERROR_SIGNIN_INVALID = "BIOMETRIC_SIGNIN_INVALID";

    /** 401: this installation's credential was revoked or expired; the app switches the feature off locally. */
    public static final String ERROR_CREDENTIAL_REVOKED = "BIOMETRIC_CREDENTIAL_REVOKED";

    /** 429: too many failed biometric sign-ins from one installation in the attempt window. */
    public static final String ERROR_TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";

    /** 400: the enrolment carries an unusable public key, algorithm, platform or installation id. */
    public static final String ERROR_INVALID_REQUEST = "BIOMETRIC_REQUEST_INVALID";

    /** 404: no such enrolled device of the caller. */
    public static final String ERROR_DEVICE_NOT_FOUND = "BIOMETRIC_DEVICE_NOT_FOUND";

    /** English fallback messages of the codes above (the apps show their own translated text). */
    public static final String MESSAGE_REAUTH_REQUIRED = "Please confirm your password to turn on fingerprint / Face ID sign-in";
    public static final String MESSAGE_SIGNIN_INVALID = "Fingerprint / Face ID sign-in failed. Please sign in with your password.";
    public static final String MESSAGE_CREDENTIAL_REVOKED = "Fingerprint / Face ID sign-in is no longer active on this device";
    public static final String MESSAGE_TOO_MANY_ATTEMPTS = "Too many attempts. Please try again later.";
    public static final String MESSAGE_DEVICE_NOT_FOUND = "Device not found";
    public static final String MESSAGE_INSTALLATION_REQUIRED = "A valid installation id is required";
    public static final String MESSAGE_APPLICATION_REQUIRED = "The X-Application-Code header is required";
    public static final String MESSAGE_KEY_ALGORITHM_REQUIRED = "keyAlgorithm must be EC_P256_SHA256 or RSA_2048_SHA256";
    public static final String MESSAGE_PLATFORM_REQUIRED = "platform must be ANDROID or IOS";
    public static final String MESSAGE_PUBLIC_KEY_INVALID = "publicKey is not a supported EC P-256 or RSA 2048 public key";
    public static final String MESSAGE_DEVICE_NAME_TOO_LONG = "deviceName is too long";
    public static final String MESSAGE_ENROL_CONFLICT = "This device was enrolled at the same moment by another request; please try again";

    /** Random bytes in a challenge before base64 encoding. */
    public static final int CHALLENGE_BYTES = 32;

    /** Installation ids: the app's random UUID (any 8-64 letters, digits and hyphens are accepted). */
    public static final Pattern INSTALLATION_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /** Longest stored device name (characters after trimming). */
    public static final int MAX_DEVICE_NAME = 100;

    /** Longest accepted public key text (base64 or PEM); a 2048-bit RSA SPKI is about 400 characters. */
    public static final int MAX_PUBLIC_KEY_CHARS = 4096;

    /** Longest accepted signature text (base64); an RSA 2048 signature is 344 characters. */
    public static final int MAX_SIGNATURE_CHARS = 1024;

    /** Longest accepted challenge id. */
    public static final int MAX_CHALLENGE_ID = 64;

    /** RSA modulus size accepted, in bits. */
    public static final int RSA_KEY_BITS = 2048;

    /** Standard name of the only EC curve accepted (NIST P-256). */
    public static final String EC_CURVE = "secp256r1";

    /** PEM armour of an SPKI public key (stripped before decoding). */
    public static final String PEM_BEGIN = "-----BEGIN PUBLIC KEY-----";
    public static final String PEM_END = "-----END PUBLIC KEY-----";

    /** Audit entity type of an enrolled device credential, and the fields of its audit entry (no key, signature or challenge). */
    public static final String AUDIT_ENTITY_TYPE = "BIOMETRIC_CREDENTIAL";
    public static final String AUDIT_FIELD_INSTALLATION_ID = "installationId";
    public static final String AUDIT_FIELD_APPLICATION_CODE = "applicationCode";
    public static final String AUDIT_FIELD_PLATFORM = "platform";
    public static final String AUDIT_FIELD_KEY_ALGORITHM = "keyAlgorithm";
    public static final String AUDIT_FIELD_REASON = "reason";
    public static final String AUDIT_FIELD_FAILURE = "failure";

    /** Failure reasons written to the audit of a failed biometric sign-in. */
    public static final String FAILURE_SIGNATURE = "SIGNATURE_INVALID";
    public static final String FAILURE_CHALLENGE = "CHALLENGE_INVALID";
    public static final String FAILURE_UNKNOWN_DEVICE = "UNKNOWN_DEVICE";
    public static final String FAILURE_CREDENTIAL_REVOKED = "CREDENTIAL_REVOKED";
    public static final String FAILURE_CREDENTIAL_EXPIRED = "CREDENTIAL_EXPIRED";
    public static final String FAILURE_RATE_LIMITED = "RATE_LIMITED";

    private BiometricConstants() {
    }
}
