package com.heuristiq.uam.biometric.application;

import com.heuristiq.uam.biometric.application.port.AccountReauthentication;
import com.heuristiq.uam.biometric.application.port.BiometricAttemptStore;
import com.heuristiq.uam.biometric.application.port.BiometricAudit;
import com.heuristiq.uam.biometric.application.port.BiometricChallengeStore;
import com.heuristiq.uam.biometric.application.port.BiometricCredentialStore;
import com.heuristiq.uam.biometric.application.port.BiometricSignIn;
import com.heuristiq.uam.biometric.domain.BiometricChallenge;
import com.heuristiq.uam.biometric.domain.BiometricConstants;
import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.uam.biometric.domain.BiometricException;
import com.heuristiq.uam.biometric.domain.BiometricKeyAlgorithm;
import com.heuristiq.uam.biometric.domain.BiometricPlatform;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import com.heuristiq.uam.biometric.domain.BiometricSignatureVerifier;
import com.heuristiq.uam.biometric.domain.ConstantTimeText;
import com.heuristiq.uam.dto.LoginResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Device-key sign-in. Enrolment stores the public key of a key pair the phone keeps behind the person's face or finger;
 * sign-in proves possession of the private key by a signature over a one-time challenge, then hands over to UAM's normal
 * sign-in (same answer as a password sign-in). Every refusal of a sign-in looks the same to the caller
 * ({@code BIOMETRIC_SIGNIN_INVALID}) except a revoked or expired credential whose own key signed correctly
 * ({@code BIOMETRIC_CREDENTIAL_REVOKED}, so the app can switch the feature off), and failures per installation are limited.
 */
public class BiometricService implements BiometricUseCase, BiometricRevocationUseCase {

    private final BiometricCredentialStore credentials;
    private final BiometricChallengeStore challenges;
    private final BiometricAttemptStore attempts;
    private final BiometricAudit audit;
    private final BiometricSignIn signIn;
    private final AccountReauthentication reauthentication;
    private final BiometricSettings settings;
    private final Clock clock;
    private final SecureRandom random;

    public BiometricService(BiometricCredentialStore credentials, BiometricChallengeStore challenges, BiometricAttemptStore attempts,
                            BiometricAudit audit, BiometricSignIn signIn, AccountReauthentication reauthentication,
                            BiometricSettings settings, Clock clock, SecureRandom random) {
        this.credentials = credentials;
        this.challenges = challenges;
        this.attempts = attempts;
        this.audit = audit;
        this.signIn = signIn;
        this.reauthentication = reauthentication;
        this.settings = settings;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public EnrolResult enrol(EnrolCommand command) {
        String userId = trimToNull(command.userId());
        if (userId == null) {
            throw BiometricException.reauthRequired();
        }
        String applicationCode = trimToNull(command.applicationCode());
        if (applicationCode == null) {
            throw BiometricException.invalidRequest(BiometricConstants.MESSAGE_APPLICATION_REQUIRED);
        }
        String installationId = requireInstallation(command.installationId());
        BiometricKeyAlgorithm algorithm = BiometricKeyAlgorithm.parse(command.keyAlgorithm());
        if (algorithm == null) {
            throw BiometricException.invalidRequest(BiometricConstants.MESSAGE_KEY_ALGORITHM_REQUIRED);
        }
        BiometricPlatform platform = BiometricPlatform.parse(command.platform());
        if (platform == null) {
            throw BiometricException.invalidRequest(BiometricConstants.MESSAGE_PLATFORM_REQUIRED);
        }
        String deviceName = trimToNull(command.deviceName());
        if (deviceName != null && deviceName.length() > BiometricConstants.MAX_DEVICE_NAME) {
            throw BiometricException.invalidRequest(BiometricConstants.MESSAGE_DEVICE_NAME_TOO_LONG);
        }
        String publicKey = BiometricSignatureVerifier.canonicalPublicKey(command.publicKey(), algorithm);
        Instant now = clock.instant();
        requireReauthentication(userId, trimToNull(command.sessionId()), command.currentPassword(), now);

        // One installation holds one key: whatever was enrolled on it before (this person's or someone else's) is replaced.
        for (BiometricCredential previous : credentials.findActiveByInstallation(applicationCode, installationId)) {
            revoke(previous, BiometricRevocationReason.REPLACED, now);
        }
        BiometricCredential created = new BiometricCredential(UUID.randomUUID().toString(), userId, trimToNull(command.tenantId()),
                applicationCode, installationId, publicKey, algorithm, platform, deviceName, BiometricCredentialStatus.ACTIVE, null,
                now, null, now.plus(settings.inactivityExpiry()), null, now);
        credentials.insert(created);
        audit.enabled(created);
        return new EnrolResult(created.credentialId(), created.createdAt(), created.expiresAt());
    }

    @Override
    public List<DeviceView> listMine(String userId, String currentInstallationId) {
        Instant now = clock.instant();
        String current = trimToNull(currentInstallationId);
        return credentials.findActiveByUser(userId).stream()
                .filter(credential -> credential.usableAt(now))
                .map(credential -> new DeviceView(credential.credentialId(), credential.deviceName(),
                        credential.platform() == null ? null : credential.platform().name(), credential.createdAt(),
                        credential.lastUsedAt(), credential.expiresAt(),
                        current != null && ConstantTimeText.equal(credential.installationId(), current)))
                .toList();
    }

    @Override
    public void revokeMine(String userId, String credentialId) {
        BiometricCredential credential = Optional.ofNullable(trimToNull(credentialId))
                .flatMap(credentials::findById)
                .filter(found -> found.userId() != null && found.userId().equals(userId))
                .orElseThrow(BiometricException::notFound);
        if (credential.status() == BiometricCredentialStatus.ACTIVE) {
            revoke(credential, BiometricRevocationReason.USER_REVOKED, clock.instant());
        }
    }

    @Override
    public void revokeMineByInstallation(String userId, String installationId) {
        String installation = requireInstallation(installationId);
        Instant now = clock.instant();
        credentials.findActiveByUser(userId).stream()
                .filter(credential -> ConstantTimeText.equal(credential.installationId(), installation))
                .forEach(credential -> revoke(credential, BiometricRevocationReason.USER_REVOKED, now));
    }

    @Override
    public ChallengeResult challenge(String installationId) {
        String installation = requireInstallation(installationId);
        byte[] bytes = new byte[BiometricConstants.CHALLENGE_BYTES];
        random.nextBytes(bytes);
        Instant now = clock.instant();
        BiometricChallenge challenge = new BiometricChallenge(UUID.randomUUID().toString(), installation,
                Base64.getEncoder().encodeToString(bytes), now, now.plus(settings.challengeTtl()));
        challenges.save(challenge);
        return new ChallengeResult(challenge.challengeId(), challenge.challenge(), challenge.expiresAt());
    }

    @Override
    public LoginResponse verify(VerifyCommand command) {
        Instant now = clock.instant();
        String installationId = trimToNull(command.installationId());
        if (installationId == null || !BiometricConstants.INSTALLATION_ID.matcher(installationId).matches()) {
            throw BiometricException.signInInvalid();
        }
        if (attempts.failures(installationId, now) >= settings.maxFailedAttempts()) {
            throw BiometricException.tooManyAttempts();
        }
        String challengeId = trimToNull(command.challengeId());
        String applicationCode = trimToNull(command.applicationCode());
        // The challenge is used up first, whatever happens next: it can never be answered twice.
        Optional<BiometricChallenge> challenge = challengeId == null || challengeId.length() > BiometricConstants.MAX_CHALLENGE_ID
                ? Optional.empty() : challenges.consume(challengeId, now);
        BiometricCredential credential = applicationCode == null ? null : credentialOf(applicationCode, installationId);
        if (challenge.isEmpty() || !ConstantTimeText.equal(challenge.get().installationId(), installationId)) {
            throw failed(credential, installationId, BiometricConstants.FAILURE_CHALLENGE, BiometricException.signInInvalid(), now);
        }
        if (credential == null) {
            throw failed(null, installationId, BiometricConstants.FAILURE_UNKNOWN_DEVICE, BiometricException.signInInvalid(), now);
        }
        if (!BiometricSignatureVerifier.verify(credential.publicKey(), credential.keyAlgorithm(), challenge.get().challenge(),
                command.signature())) {
            throw failed(credential, installationId, BiometricConstants.FAILURE_SIGNATURE, BiometricException.signInInvalid(), now);
        }
        // Only a correct signature by the credential's own key learns that it was revoked or expired.
        if (credential.status() == BiometricCredentialStatus.ACTIVE && credential.expiredAt(now)) {
            if (credentials.expireIfActive(credential.credentialId(), now)) {
                audit.revoked(credential, BiometricRevocationReason.INACTIVITY);
            }
            throw failed(credential, installationId, BiometricConstants.FAILURE_CREDENTIAL_EXPIRED,
                    BiometricException.credentialRevoked(), now);
        }
        if (credential.status() != BiometricCredentialStatus.ACTIVE) {
            throw failed(credential, installationId, BiometricConstants.FAILURE_CREDENTIAL_REVOKED,
                    BiometricException.credentialRevoked(), now);
        }
        LoginResponse response = signIn.signIn(credential.userId());
        credentials.touch(credential.credentialId(), now, now.plus(settings.inactivityExpiry()));
        attempts.clear(installationId);
        audit.signedIn(credential);
        return response;
    }

    @Override
    public int revokeAllForUser(String userId, BiometricRevocationReason reason) {
        if (trimToNull(userId) == null) {
            return 0;
        }
        Instant now = clock.instant();
        int revoked = 0;
        for (BiometricCredential credential : credentials.findActiveByUser(userId)) {
            if (revoke(credential, reason, now)) {
                revoked++;
            }
        }
        return revoked;
    }

    /** The installation's ACTIVE credential for the application, else its latest one of any status, else null. */
    private BiometricCredential credentialOf(String applicationCode, String installationId) {
        return credentials.findActiveByInstallation(applicationCode, installationId).stream().findFirst()
                .or(() -> credentials.findLatestByInstallation(applicationCode, installationId))
                .orElse(null);
    }

    /**
     * Counts a failed sign-in and audits it when the credential is known, plus once when the installation reaches the limit.
     * Returns the exception to throw.
     */
    private BiometricException failed(BiometricCredential credential, String installationId, String failure,
                                      BiometricException refusal, Instant now) {
        int failures = attempts.recordFailure(installationId, now, settings.attemptWindow());
        if (credential != null) {
            audit.signInFailed(credential, installationId, failure);
        }
        if (failures == settings.maxFailedAttempts()) {
            audit.signInFailed(credential, installationId, BiometricConstants.FAILURE_RATE_LIMITED);
        }
        return refusal;
    }

    private void requireReauthentication(String userId, String sessionId, String currentPassword, Instant now) {
        if (currentPassword != null && !currentPassword.isEmpty()) {
            if (reauthentication.passwordMatches(userId, currentPassword)) {
                return;
            }
            throw BiometricException.reauthRequired();
        }
        if (sessionId != null) {
            Optional<Instant> signedInAt = reauthentication.interactiveSignInAt(sessionId, userId);
            if (signedInAt.isPresent() && !signedInAt.get().isBefore(now.minus(settings.reauthWindow()))) {
                return;
            }
        }
        throw BiometricException.reauthRequired();
    }

    private boolean revoke(BiometricCredential credential, BiometricRevocationReason reason, Instant now) {
        if (!credentials.revokeIfActive(credential.credentialId(), reason, now)) {
            return false;
        }
        audit.revoked(credential, reason);
        return true;
    }

    private static String requireInstallation(String installationId) {
        String trimmed = trimToNull(installationId);
        if (trimmed == null || !BiometricConstants.INSTALLATION_ID.matcher(trimmed).matches()) {
            throw BiometricException.invalidRequest(BiometricConstants.MESSAGE_INSTALLATION_REQUIRED);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
