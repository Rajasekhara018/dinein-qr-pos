package com.heuristiq.identity.biometric.application;

import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;

/**
 * The one entry point other UAM features call when an account's device credentials must stop working: password change or
 * reset, "log out of all devices", suspension, deletion, removal from an organization. Callers reach it through
 * {@link BiometricRevocations}, which resolves it lazily (no bean cycle).
 */
public interface BiometricRevocationUseCase {

    /**
     * Revokes every ACTIVE credential of the user, auditing each one with {@code reason}.
     *
     * @return how many were revoked
     */
    int revokeAllForUser(String userId, BiometricRevocationReason reason);
}
