package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums.MfaType;

/**
 * Returned by {@code AuthService.login()} in place of {@link LoginResponse} when the password was
 * correct but the account has an enabled MFA method — the caller resubmits the same login request
 * with {@code totpCode} populated to complete sign-in.
 */
public record MfaChallengeRequiredResponse(
        boolean mfaRequired,
        MfaType mfaType,
        String message
) implements LoginResult {
    public static MfaChallengeRequiredResponse required(MfaType mfaType) {
        return new MfaChallengeRequiredResponse(true, mfaType, "Enter your authenticator code to finish signing in.");
    }
}
