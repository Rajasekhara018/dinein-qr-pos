package com.heuristiq.identity.biometric.application;

import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import org.springframework.beans.factory.ObjectProvider;

/**
 * How the revocation hooks (password, sessions, account status, membership) call {@link BiometricRevocationUseCase}. The
 * use case is resolved lazily through an {@link ObjectProvider}, because biometric sign-in itself depends on the sign-in
 * machinery those features belong to; a hook built without it (unit tests) does nothing. A failure is not swallowed: a
 * credential that should have been revoked must not silently stay active.
 */
public final class BiometricRevocations {

    private BiometricRevocations() {
    }

    /** Revokes every ACTIVE credential of {@code userId}; a no-op when {@code revocation} is null or has no bean. */
    public static void revokeAll(ObjectProvider<BiometricRevocationUseCase> revocation, String userId, BiometricRevocationReason reason) {
        if (revocation == null || userId == null || userId.isBlank()) {
            return;
        }
        BiometricRevocationUseCase useCase = revocation.getIfAvailable();
        if (useCase != null) {
            useCase.revokeAllForUser(userId, reason);
        }
    }
}
