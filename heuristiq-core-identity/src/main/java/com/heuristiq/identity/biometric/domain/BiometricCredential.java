package com.heuristiq.identity.biometric.domain;

import java.time.Instant;

/**
 * UAM's record of one enrolled app installation: the public half of a key pair whose private half stays in the phone's
 * Secure Enclave / Android Keystore and is unlocked only by the person's face or fingerprint. A credential belongs to one
 * user (it is identity, not organization data); {@code tenantId} records the organization the enrolment was made in, for
 * the audit trail only. At most one ACTIVE credential exists per application and installation.
 *
 * @param credentialId   storage id, also shown to the person (UUID)
 * @param userId         the account it signs in
 * @param tenantId       organization of the session that enrolled it (informational)
 * @param applicationCode the application (X-Application-Code) it was enrolled for; it signs in to that application only
 * @param installationId the app's random per-install id
 * @param publicKey      base64 of the X.509 SubjectPublicKeyInfo DER (canonical form, PEM armour removed)
 * @param keyAlgorithm   key and signature scheme
 * @param platform       ANDROID or IOS
 * @param deviceName     the person's name for the phone (may be null)
 * @param status         ACTIVE, REVOKED or EXPIRED
 * @param revokedReason  why it is no longer ACTIVE (null while ACTIVE)
 * @param createdAt      enrolment time
 * @param lastUsedAt     last successful sign-in (null until the first)
 * @param expiresAt      end of the inactivity window: {@code (lastUsedAt or createdAt) + window}
 * @param revokedAt      when it left ACTIVE (null while ACTIVE)
 * @param updatedAt      last change
 */
public record BiometricCredential(
        String credentialId,
        String userId,
        String tenantId,
        String applicationCode,
        String installationId,
        String publicKey,
        BiometricKeyAlgorithm keyAlgorithm,
        BiometricPlatform platform,
        String deviceName,
        BiometricCredentialStatus status,
        BiometricRevocationReason revokedReason,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant updatedAt) {

    /** True while ACTIVE and inside its inactivity window at {@code now}. */
    public boolean usableAt(Instant now) {
        return status == BiometricCredentialStatus.ACTIVE && !expiredAt(now);
    }

    /** True when the inactivity window has ended at {@code now} (whatever the stored status says). */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
