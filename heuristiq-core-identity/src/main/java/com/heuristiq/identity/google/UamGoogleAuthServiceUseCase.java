package com.heuristiq.identity.google;

import com.heuristiq.identity.dto.LoginResult;
import com.heuristiq.identity.enums.UamEnums;

public interface UamGoogleAuthServiceUseCase {

    record LoginInput(String idToken) {}
    record LoginOutput(LoginResult response) {}
    LoginOutput login(LoginInput input);

    /**
     * {@code totpCode} is only needed for users with MFA enrolled: without it they get a
     * {@link com.heuristiq.identity.dto.MfaChallengeRequiredResponse} instead of a session.
     */
    record LoginWithTypeInput(String idToken, UamEnums.UserType userType, String totpCode) {
        public LoginWithTypeInput(String idToken, UamEnums.UserType userType) {
            this(idToken, userType, null);
        }
    }
    /** A {@link com.heuristiq.identity.dto.LoginResponse}, or an MFA challenge for users with MFA enrolled and no totpCode. */
    record LoginWithTypeOutput(LoginResult response) {}
    LoginWithTypeOutput login(LoginWithTypeInput input);
}
