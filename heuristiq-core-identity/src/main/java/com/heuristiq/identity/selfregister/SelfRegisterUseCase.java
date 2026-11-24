package com.heuristiq.uam.selfregister;

/**
 * Proves, before an account exists, that the person signing up controls the email address: a one-time code is emailed, and
 * entering it returns a single-use token that the onboarding service spends when the sign-up is submitted. Nothing here
 * creates an account, sets a password or reveals whether an address is already registered.
 */
public interface SelfRegisterUseCase {

    /**
     * Emails a code to the address (or, for an address that already has an account, tells its owner so, with no code).
     * The answer is the same either way.
     *
     * @throws com.heuristiq.uam.common.UamException 403 when sign-up is switched off, 400 for a blocked address, a failed bot check
     *         or a bad application code, 429 when the address or the client has asked too often
     */
    SendResult sendOtp(String email, String applicationCode, String botToken, String clientAddress);

    /**
     * Checks the code and, when right, spends it and returns a single-use token bound to this email and application.
     *
     * @throws com.heuristiq.uam.common.UamException 400 {@code OTP_INVALID} for a wrong, expired or used code
     */
    VerifyResult verifyOtp(String email, String otp, String applicationCode);

    /** Spends the token: true exactly once for a token this service issued for this email and application, still within its lifetime. */
    boolean consumeToken(String email, String token, String applicationCode);

    /**
     * Emails the code with which a newly created member chooses their own password (the same code and the same
     * {@code reset-password} call as "Forgot password"). Best effort: no account for the address, or a code sent a moment ago,
     * means nothing is sent and nothing is reported, so it never reveals anything.
     */
    void sendSetPasswordCode(String email);

    /** @param resendAfterSeconds the earliest the same address may ask for another code */
    record SendResult(boolean sent, long resendAfterSeconds) {
    }

    /** @param verificationToken opaque, single use; @param expiresInSeconds how long it stays valid */
    record VerifyResult(String verificationToken, long expiresInSeconds) {
    }
}
