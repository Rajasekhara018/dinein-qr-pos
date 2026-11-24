package com.heuristiq.uam.biometric.application.port;

import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;

/**
 * Domain audit of biometric sign-in (central audit service). Entries carry ids, the installation, application, platform
 * and reasons, never a public key, signature or challenge. Never fails the action being audited.
 */
public interface BiometricAudit {

    /** BIOMETRIC_ENABLED: a device was enrolled. */
    void enabled(BiometricCredential credential);

    /** BIOMETRIC_SIGNIN: a device signed its account in. */
    void signedIn(BiometricCredential credential);

    /**
     * BIOMETRIC_SIGNIN_FAILED: a refused sign-in.
     *
     * @param credential the installation's credential when one is known (null for an unknown installation)
     * @param installationId the installation that tried
     * @param failure one of the {@code FAILURE_*} reasons of {@code BiometricConstants}
     */
    void signInFailed(BiometricCredential credential, String installationId, String failure);

    /** BIOMETRIC_REVOKED: a credential stopped working, with the reason. */
    void revoked(BiometricCredential credential, BiometricRevocationReason reason);
}
