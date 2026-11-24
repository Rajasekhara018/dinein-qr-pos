package com.heuristiq.uam.biometric.adapter.out;

import com.heuristiq.uam.auth.AuthServiceUseCase;
import com.heuristiq.uam.biometric.application.port.BiometricSignIn;
import com.heuristiq.uam.biometric.domain.BiometricException;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;

/**
 * Hands a proven device signature over to UAM's sign-in ({@link AuthServiceUseCase#loginWithBiometric}): lockout, account
 * status, organization and application checks, session and tokens are exactly those of a password sign-in.
 */
public class AuthServiceBiometricSignIn implements BiometricSignIn {

    private final UamUserRepository users;
    private final AuthServiceUseCase auth;

    public AuthServiceBiometricSignIn(UamUserRepository users, AuthServiceUseCase auth) {
        this.users = users;
        this.auth = auth;
    }

    @Override
    public LoginResponse signIn(String userId) {
        UamUser user = users.findById(userId).orElseThrow(BiometricException::signInInvalid);
        return auth.loginWithBiometric(new AuthServiceUseCase.LoginWithBiometricInput(user)).response();
    }
}
