package com.heuristiq.uam.selfregister;

import java.util.regex.Pattern;

/** Fixed values of the self-registration email check. Tunable values live in {@code railcab.uam.self-register.*}. */
public final class SelfRegisterConstants {

    /** Public path of the self-registration calls, under the public {@code identity/auth/**} area (no privilege entry). */
    public static final String PUBLIC_BASE = "/api/v1/uam/identity/auth/self-register";

    /** Service-to-service path used by the onboarding service to spend a verification token. */
    public static final String INTERNAL_BASE = "/api/v1/uam/internal/self-register";

    /** Key prefix of the emailed one-time code. Never equal to a sign-in key (those start with a user type). */
    static final String EMAIL_KEY_PREFIX = "SELFREG:EMAIL:";

    /** Key prefix of the single-use verification token handed back once the code is right. */
    static final String TOKEN_KEY_PREFIX = "SELFREG:TOKEN:";

    /** Key prefix of the per-address send counter (the address is hashed, never stored). */
    static final String ADDRESS_KEY_PREFIX = "SELFREG:ADDR:";

    /** Separator inside a key. */
    static final String KEY_SEPARATOR = ":";

    /** Random bytes in a verification token. */
    static final int TOKEN_BYTES = 32;

    /** The code is six digits, as for every other one-time code on the platform. */
    static final int OTP_DIGITS_BOUND = 1_000_000;

    /** What the code is for, shown in the email. */
    static final String OTP_PURPOSE = "Create your account";

    /** Application codes are upper-case words (for example {@code PASUPU_KUMKUMA_MATRIMONY_APP}). */
    static final Pattern APPLICATION_CODE = Pattern.compile("^[A-Z0-9_]{1,64}$");

    /** The value stored as the "code" of the per-address counter: nothing secret, the counter is what matters. */
    static final String ADDRESS_COUNTER_VALUE = "-";

    /** Error codes returned in the response envelope. */
    public static final String ERROR_DISABLED = "SELF_REGISTRATION_DISABLED";
    public static final String ERROR_OTP_INVALID = "OTP_INVALID";
    public static final String ERROR_RATE_LIMITED = "OTP_RATE_LIMITED";
    public static final String ERROR_BOT_CHECK = "BOT_CHECK_FAILED";
    public static final String ERROR_EMAIL_NOT_ALLOWED = "EMAIL_NOT_ALLOWED";
    public static final String ERROR_APPLICATION_CODE = "APPLICATION_CODE_INVALID";

    /** Messages. */
    static final String MESSAGE_DISABLED = "Creating an account is not available right now.";
    static final String MESSAGE_RATE_LIMITED = "Too many requests. Please try again later.";
    static final String MESSAGE_BOT_CHECK = "We could not confirm this request. Please try again.";
    static final String MESSAGE_EMAIL_NOT_ALLOWED = "That email address cannot be used to create an account.";
    static final String MESSAGE_APPLICATION_CODE = "The application is not valid.";
    static final String MESSAGE_SENT = "If this address can be used, a code is on its way.";

    private SelfRegisterConstants() {
    }
}
