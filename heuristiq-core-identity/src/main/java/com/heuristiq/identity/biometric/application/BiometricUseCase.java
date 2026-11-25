package com.heuristiq.identity.biometric.application;

import com.heuristiq.identity.dto.LoginResponse;
import java.time.Instant;
import java.util.List;

/**
 * "Sign in with fingerprint / Face ID": a signed-in person enrols the app installation's device key, lists and revokes their
 * own devices; anyone may ask for a challenge and sign in with a device signature over it.
 */
public interface BiometricUseCase {

    /**
     * Enrolment request (the account, organization, application and session come from the token and headers, never the body).
     *
     * @param userId          the token's subject
     * @param tenantId        the session's organization (recorded for the audit trail)
     * @param applicationCode the {@code X-Application-Code} header
     * @param sessionId       the token's session id (null when the token has none)
     * @param currentPassword optional: proves the person when the session was not signed in recently
     */
    record EnrolCommand(String userId, String tenantId, String applicationCode, String sessionId, String installationId,
                        String publicKey, String keyAlgorithm, String platform, String deviceName, String currentPassword) {
        @Override
        public String toString() {
            // Never print the password.
            return "EnrolCommand[userId=" + userId + ", applicationCode=" + applicationCode + ", installationId=" + installationId + "]";
        }
    }

    record EnrolResult(String credentialId, Instant createdAt, Instant expiresAt) {}

    /** One of the caller's ACTIVE devices; {@code current} when it is the installation making the call. */
    record DeviceView(String credentialId, String deviceName, String platform, Instant createdAt, Instant lastUsedAt,
                      Instant expiresAt, boolean current) {}

    record ChallengeResult(String challengeId, String challenge, Instant expiresAt) {}

    /** Sign-in request; {@code applicationCode} is the {@code X-Application-Code} header. */
    record VerifyCommand(String challengeId, String installationId, String signature, String applicationCode) {}

    /**
     * Enrols (or re-enrols, replacing the previous key of) this installation.
     *
     * @throws com.heuristiq.identity.biometric.domain.BiometricException REAUTH_REQUIRED without a recent interactive sign-in or a
     *                                                             correct current password; INVALID_REQUEST for a bad key etc.
     */
    EnrolResult enrol(EnrolCommand command);

    /** The caller's ACTIVE, unexpired devices, newest first. */
    List<DeviceView> listMine(String userId, String currentInstallationId);

    /**
     * Revokes one of the caller's devices.
     *
     * @throws com.heuristiq.identity.biometric.domain.BiometricException NOT_FOUND when it is not one of the caller's
     */
    void revokeMine(String userId, String credentialId);

    /** Revokes the caller's ACTIVE credentials of one installation (turning the feature off on this phone); idempotent. */
    void revokeMineByInstallation(String userId, String installationId);

    /** A fresh one-time challenge; always answered, even for an installation that never enrolled. */
    ChallengeResult challenge(String installationId);

    /**
     * Signs in with a device signature over a challenge.
     *
     * @return the same answer a password sign-in gives
     * @throws com.heuristiq.identity.biometric.domain.BiometricException SIGNIN_INVALID, CREDENTIAL_REVOKED or TOO_MANY_ATTEMPTS
     */
    LoginResponse verify(VerifyCommand command);
}
