package com.heuristiq.identity.biometric.application.port;

import com.heuristiq.identity.dto.LoginResponse;

/**
 * UAM's existing sign-in machinery, seen from biometric sign-in: once the device signature is proven, the session is issued
 * exactly like a password sign-in (organization from {@code X-Organization-Code}, application access from
 * {@code X-Application-Code}, membership and franchise checks, security policy, refresh token), with the second factor
 * counted as satisfied and the session marked as a biometric one.
 */
public interface BiometricSignIn {

    /**
     * Signs the account in.
     *
     * @return the same answer a password sign-in gives
     * @throws com.heuristiq.identity.biometric.domain.BiometricException SIGNIN_INVALID when the account no longer exists
     * @throws com.heuristiq.identity.common.UamException the same refusals password sign-in gives (account not active, locked out,
     *                                             no access to the organization or application)
     */
    LoginResponse signIn(String userId);
}
