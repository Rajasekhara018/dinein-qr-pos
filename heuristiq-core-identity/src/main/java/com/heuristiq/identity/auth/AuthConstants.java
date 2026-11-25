package com.heuristiq.identity.auth;

/**
 * Fixed values of the sign-in feature: request headers, refresh-token shape, audit entity type and the
 * messages shown when a sign-in is refused.
 */
public final class AuthConstants {

    /**
     * Account types the public register endpoint may create (an allow-list, so a type added to the enum later is refused
     * until someone decides it is self-registrable). Staff types are never on it.
     */
    public static final java.util.Set<com.heuristiq.identity.enums.UamEnums.UserType> SELF_REGISTRABLE_USER_TYPES = java.util.EnumSet.of(
            com.heuristiq.identity.enums.UamEnums.UserType.CUSTOMER, com.heuristiq.identity.enums.UamEnums.UserType.DRIVER,
            com.heuristiq.identity.enums.UamEnums.UserType.VENDOR, com.heuristiq.identity.enums.UamEnums.UserType.BUSINESS);

    /**
     * Account types a first Google or external-IdP sign-in may create: the self-registrable ones plus OPS, the
     * back-office type Rider_Web's sign-in asks for (an OPS account starts with no organization membership and no
     * privileges). SUPER_ADMIN, ADMIN and SECURITY are never created by a sign-in; they are provisioned by an admin.
     */
    public static final java.util.Set<com.heuristiq.identity.enums.UamEnums.UserType> SIGN_IN_PROVISIONABLE_USER_TYPES = java.util.EnumSet.of(
            com.heuristiq.identity.enums.UamEnums.UserType.CUSTOMER, com.heuristiq.identity.enums.UamEnums.UserType.DRIVER,
            com.heuristiq.identity.enums.UamEnums.UserType.VENDOR, com.heuristiq.identity.enums.UamEnums.UserType.BUSINESS,
            com.heuristiq.identity.enums.UamEnums.UserType.OPS);

    /** Refusal when a public flow asks for an account type it may not create (403). */
    public static final String ACCOUNT_TYPE_NOT_ALLOWED_MESSAGE = "That account type cannot be registered here.";

    /** Optional request header naming the registered application the caller is signing in to. */
    public static final String APPLICATION_CODE_HEADER = "X-Application-Code";

    /** Proxy header listing the client address followed by each proxy hop. */
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** Proxy header carrying the client address seen by the front proxy. */
    public static final String REAL_IP_HEADER = "X-Real-IP";

    /** Value some proxies put in a forwarding header when they do not know the address. */
    public static final String UNKNOWN_ADDRESS = "unknown";

    /** Longest textual IP literal (a full IPv6 address with an embedded IPv4 part). */
    public static final int MAX_IP_LITERAL_LENGTH = 45;

    /** Client type recorded on refresh tokens issued at sign-in. */
    public static final String REFRESH_TOKEN_CLIENT_TYPE = "web";

    /** Random bytes in a refresh token before encoding. */
    public static final int REFRESH_TOKEN_BYTES = 32;

    /** Session auth method (UserSession.authMethod) of a device-key (fingerprint / Face ID) sign-in. */
    public static final String AUTH_METHOD_BIOMETRIC = "BIOMETRIC";

    /** The token's amr value of a biometric sign-in. */
    public static final String AMR_BIOMETRIC = "biometric";

    /** Audit entity type of a sign-in session. */
    public static final String AUTH_SESSION_ENTITY = "AUTH_SESSION";

    /** Shown when the account is locked out after too many failed attempts. */
    public static final String LOGIN_LOCKED_MESSAGE = "Too many failed login attempts. Please try again later.";

    /** Shown when the authenticator (TOTP) code is wrong. */
    public static final String INVALID_AUTHENTICATOR_CODE_MESSAGE = "Invalid authenticator code";

    /** Shown when the user name or password is wrong (deliberately not saying which). */
    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

    /** Google sign-in by an application that names itself, for an email that has no account (nothing is created for it). */
    public static final String GOOGLE_ACCOUNT_NOT_REGISTERED_MESSAGE =
            "No account found for this Google email. Please register first.";

    /**
     * Error code (403) of a sign-in or refresh refused because the account's only access to the application came from franchise
     * roles and none of its franchises is ACTIVE (closed, suspended, pending or none). Clients map it to their own translated text.
     */
    public static final String ERROR_FRANCHISE_INACTIVE = "FRANCHISE_INACTIVE";

    /**
     * Back-office account types (platform and organization administration, operations, security). An account holding one of them
     * never signs in to a customer app (a sign-in naming a self-service user type), and is never put through onboarding.
     */
    public static final java.util.Set<com.heuristiq.identity.enums.UamEnums.UserType> BACK_OFFICE_USER_TYPES = java.util.EnumSet.of(
            com.heuristiq.identity.enums.UamEnums.UserType.SUPER_ADMIN, com.heuristiq.identity.enums.UamEnums.UserType.ADMIN,
            com.heuristiq.identity.enums.UamEnums.UserType.OPS, com.heuristiq.identity.enums.UamEnums.UserType.SECURITY);

    /** Error code of a back-office account signing in to a customer app. Clients map it to their own translated text. */
    public static final String ERROR_STAFF_ACCOUNT_NOT_ALLOWED = "STAFF_ACCOUNT_NOT_ALLOWED";

    /** Shown with {@link #ERROR_STAFF_ACCOUNT_NOT_ALLOWED}; English fallback for clients that do not map the code. */
    public static final String STAFF_ACCOUNT_NOT_ALLOWED_MESSAGE =
            "Staff accounts cannot sign in to customer apps. Please use a customer account.";

    /** Shown with {@link #ERROR_FRANCHISE_INACTIVE}; English fallback for clients that do not map the code. */
    public static final String FRANCHISE_INACTIVE_MESSAGE =
            "Your franchise is not active, so you cannot sign in with your franchise role. Please contact your organization's administrator.";

    /** Reason recorded in the sign-in audit for {@link #ERROR_FRANCHISE_INACTIVE}. */
    public static final String FRANCHISE_INACTIVE_AUDIT_REASON = "No active franchise for the franchise role(s)";

    private AuthConstants() {
    }
}
