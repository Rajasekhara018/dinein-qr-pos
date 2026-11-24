package com.heuristiq.uam.google;

import com.heuristiq.uam.dto.LoginResult;
import com.heuristiq.uam.enums.UamEnums;

public interface UamGoogleAuthServiceUseCase {

    record LoginInput(String idToken) {}
    record LoginOutput(LoginResult response) {}
    LoginOutput login(LoginInput input);

    /**
     * {@code totpCode} is only needed for users with MFA enrolled: without it they get a
     * {@link com.heuristiq.uam.dto.MfaChallengeRequiredResponse} instead of a session.
     */
    record LoginWithTypeInput(String idToken, UamEnums.UserType userType, String totpCode) {
        public LoginWithTypeInput(String idToken, UamEnums.UserType userType) {
            this(idToken, userType, null);
        }
    }
    /** A {@link com.heuristiq.uam.dto.LoginResponse}, or an MFA challenge for users with MFA enrolled and no totpCode. */
    record LoginWithTypeOutput(LoginResult response) {}
    LoginWithTypeOutput login(LoginWithTypeInput input);
}
