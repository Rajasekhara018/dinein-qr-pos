package com.heuristiq.uam.auth;

import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.dto.LoginRequest;
import com.heuristiq.uam.dto.LoginResponse;

public interface AuthServiceUseCase {

    /**
     * Result is either a {@link LoginResponse} (success) or a
     * {@link com.heuristiq.uam.dto.MfaChallengeRequiredResponse} (password correct, account has 2FA
     * enabled, caller must resubmit with {@code totpCode}).
     */
    record LoginInput(LoginRequest request) {}
    record LoginOutput(Object result) {}
    LoginOutput login(LoginInput input);

    /**
     * Bridges an already externally-authenticated user (Keycloak/OIDC) onto the exact same
     * session + refresh-token + JWT machinery every other login path uses.
     */
    record LoginViaExternalProviderInput(UamUser user, String totpCode) {
        public LoginViaExternalProviderInput(UamUser user) { this(user, null); }
    }
    record LoginViaExternalProviderOutput(LoginResponse response) {}
    LoginViaExternalProviderOutput loginViaExternalProvider(LoginViaExternalProviderInput input);

    /**
     * Signs in a user whose possession of an enrolled device key was just proven (fingerprint / Face ID sign-in). The same
     * gate as password sign-in applies (account lockout, account ACTIVE) and the same session, refresh token, organization,
     * application-access and security-policy machinery runs; the device key counts as the second factor, and the session
     * is recorded as a biometric one.
     */
    record LoginWithBiometricInput(UamUser user) {}
    record LoginWithBiometricOutput(LoginResponse response) {}
    LoginWithBiometricOutput loginWithBiometric(LoginWithBiometricInput input);

    /**
     * Redeems a refresh token: verifies it, rotates it (old one revoked, new one issued), keeps
     * the linked session alive, and returns a fresh access token alongside the new refresh token.
     */
    record RefreshInput(String rawRefreshToken) {}
    record RefreshOutput(LoginResponse response) {}
    RefreshOutput refresh(RefreshInput input);
}
