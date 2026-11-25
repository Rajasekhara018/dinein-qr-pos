package com.heuristiq.identity.otp;

import java.time.Duration;
import java.util.Set;

/** Fixed values shared by the OTP classes. Tunable thresholds live in {@code railcab.uam.otp.*} properties. */
public final class OtpConstants {

    /** Size of the per-challenge random salt, in bytes. */
    public static final int SALT_BYTES = 16;

    /** Length of the sliding window the hourly send cap applies to. */
    public static final Duration SEND_WINDOW = Duration.ofHours(1);

    /** Optimistic-write retries before a contended challenge is treated as rejected. */
    public static final int MAX_CAS_RETRIES = 6;

    /** Same message for wrong, expired, consumed and locked-out OTPs, so the reason is never revealed. */
    public static final String INVALID_OTP_MESSAGE = "Invalid or expired OTP.";

    /** Generic throttle message; does not say which limit was hit. */
    public static final String TOO_MANY_REQUESTS_MESSAGE = "Too many requests. Please try again later.";

    /** Profiles in which railcab.uam.otp.expose-otp may be true. */
    public static final Set<String> EXPOSE_OTP_ALLOWED_PROFILES = Set.of("local", "dev", "test");

    /** Additive profiles that do not by themselves make an environment production-like (storage/secrets choices). */
    public static final Set<String> NEUTRAL_PROFILES = Set.of("postgres", "vault");

    /** Purpose text sent with a sign-in OTP. */
    public static final String SIGN_IN_OTP_PURPOSE = "Sign-in verification";

    private OtpConstants() {
    }
}
