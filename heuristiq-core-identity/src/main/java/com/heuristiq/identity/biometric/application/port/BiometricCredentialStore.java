package com.heuristiq.identity.biometric.application.port;

import com.heuristiq.identity.biometric.domain.BiometricCredential;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage of enrolled device credentials (PostgreSQL table or MongoDB collection {@code biometric_credentials}). Every
 * state change is a conditional update on the stored status, so concurrent requests cannot both succeed on one credential.
 */
public interface BiometricCredentialStore {

    /**
     * Stores a new ACTIVE credential.
     *
     * @throws com.heuristiq.identity.biometric.domain.BiometricException CONFLICT when another ACTIVE credential of the same
     *                                                             application and installation exists (a racing enrolment)
     */
    void insert(BiometricCredential credential);

    Optional<BiometricCredential> findById(String credentialId);

    /** ACTIVE credentials of one application and installation (normally at most one). */
    List<BiometricCredential> findActiveByInstallation(String applicationCode, String installationId);

    /** The most recently created credential of one application and installation, whatever its status. */
    Optional<BiometricCredential> findLatestByInstallation(String applicationCode, String installationId);

    /** ACTIVE credentials of one user, newest first. */
    List<BiometricCredential> findActiveByUser(String userId);

    /** Marks the credential REVOKED, only while it is ACTIVE; true when this call changed it. */
    boolean revokeIfActive(String credentialId, BiometricRevocationReason reason, Instant at);

    /** Marks the credential EXPIRED (reason INACTIVITY), only while it is ACTIVE; true when this call changed it. */
    boolean expireIfActive(String credentialId, Instant at);

    /** Records a successful sign-in and slides the inactivity window, only while ACTIVE; true when this call changed it. */
    boolean touch(String credentialId, Instant lastUsedAt, Instant expiresAt);
}
