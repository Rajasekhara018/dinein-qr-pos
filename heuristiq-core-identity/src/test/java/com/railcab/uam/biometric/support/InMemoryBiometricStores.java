package com.heuristiq.uam.biometric.support;

import com.heuristiq.uam.biometric.application.port.BiometricAttemptStore;
import com.heuristiq.uam.biometric.application.port.BiometricAudit;
import com.heuristiq.uam.biometric.application.port.BiometricChallengeStore;
import com.heuristiq.uam.biometric.application.port.BiometricCredentialStore;
import com.heuristiq.uam.biometric.domain.BiometricChallenge;
import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.uam.biometric.domain.BiometricException;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory stand-ins for the biometric ports with the same conditional semantics as the database stores. */
public final class InMemoryBiometricStores {

    private InMemoryBiometricStores() {
    }

    public static final class Credentials implements BiometricCredentialStore {
        private final Map<String, BiometricCredential> rows = new LinkedHashMap<>();

        @Override
        public synchronized void insert(BiometricCredential credential) {
            boolean clash = rows.values().stream().anyMatch(c -> c.status() == BiometricCredentialStatus.ACTIVE
                    && c.applicationCode().equals(credential.applicationCode()) && c.installationId().equals(credential.installationId()));
            if (clash) {
                throw BiometricException.conflict();
            }
            rows.put(credential.credentialId(), credential);
        }

        @Override
        public Optional<BiometricCredential> findById(String credentialId) {
            return Optional.ofNullable(rows.get(credentialId));
        }

        @Override
        public List<BiometricCredential> findActiveByInstallation(String applicationCode, String installationId) {
            return newestFirst(rows.values().stream().filter(c -> c.status() == BiometricCredentialStatus.ACTIVE
                    && c.applicationCode().equals(applicationCode) && c.installationId().equals(installationId)).toList());
        }

        @Override
        public Optional<BiometricCredential> findLatestByInstallation(String applicationCode, String installationId) {
            return newestFirst(rows.values().stream().filter(c -> c.applicationCode().equals(applicationCode)
                    && c.installationId().equals(installationId)).toList()).stream().findFirst();
        }

        @Override
        public List<BiometricCredential> findActiveByUser(String userId) {
            return newestFirst(rows.values().stream().filter(c -> c.status() == BiometricCredentialStatus.ACTIVE
                    && c.userId().equals(userId)).toList());
        }

        @Override
        public synchronized boolean revokeIfActive(String credentialId, BiometricRevocationReason reason, Instant at) {
            return end(credentialId, BiometricCredentialStatus.REVOKED, reason, at);
        }

        @Override
        public synchronized boolean expireIfActive(String credentialId, Instant at) {
            return end(credentialId, BiometricCredentialStatus.EXPIRED, BiometricRevocationReason.INACTIVITY, at);
        }

        @Override
        public synchronized boolean touch(String credentialId, Instant lastUsedAt, Instant expiresAt) {
            BiometricCredential c = rows.get(credentialId);
            if (c == null || c.status() != BiometricCredentialStatus.ACTIVE) {
                return false;
            }
            rows.put(credentialId, new BiometricCredential(c.credentialId(), c.userId(), c.tenantId(), c.applicationCode(),
                    c.installationId(), c.publicKey(), c.keyAlgorithm(), c.platform(), c.deviceName(), c.status(), c.revokedReason(),
                    c.createdAt(), lastUsedAt, expiresAt, c.revokedAt(), lastUsedAt));
            return true;
        }

        /** Test helper: put a row as stored. */
        public void put(BiometricCredential credential) {
            rows.put(credential.credentialId(), credential);
        }

        public List<BiometricCredential> all() {
            return new ArrayList<>(rows.values());
        }

        private boolean end(String credentialId, BiometricCredentialStatus status, BiometricRevocationReason reason, Instant at) {
            BiometricCredential c = rows.get(credentialId);
            if (c == null || c.status() != BiometricCredentialStatus.ACTIVE) {
                return false;
            }
            rows.put(credentialId, new BiometricCredential(c.credentialId(), c.userId(), c.tenantId(), c.applicationCode(),
                    c.installationId(), c.publicKey(), c.keyAlgorithm(), c.platform(), c.deviceName(), status, reason,
                    c.createdAt(), c.lastUsedAt(), c.expiresAt(), at, at));
            return true;
        }

        private static List<BiometricCredential> newestFirst(List<BiometricCredential> list) {
            return list.stream().sorted(Comparator.comparing(BiometricCredential::createdAt).reversed()).toList();
        }
    }

    public static final class Challenges implements BiometricChallengeStore {
        private final Map<String, BiometricChallenge> rows = new LinkedHashMap<>();
        private final Set<String> used = new HashSet<>();

        @Override
        public synchronized void save(BiometricChallenge challenge) {
            rows.put(challenge.challengeId(), challenge);
        }

        @Override
        public synchronized Optional<BiometricChallenge> consume(String challengeId, Instant now) {
            BiometricChallenge challenge = rows.get(challengeId);
            if (challenge == null || used.contains(challengeId) || !challenge.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            used.add(challengeId);
            return Optional.of(challenge);
        }

        public Optional<BiometricChallenge> find(String challengeId) {
            return Optional.ofNullable(rows.get(challengeId));
        }
    }

    public static final class Attempts implements BiometricAttemptStore {
        private record Window(int failures, Instant expiresAt) {}

        private final Map<String, Window> rows = new LinkedHashMap<>();

        @Override
        public synchronized int failures(String installationId, Instant now) {
            Window window = rows.get(installationId);
            return window == null || !window.expiresAt().isAfter(now) ? 0 : window.failures();
        }

        @Override
        public synchronized int recordFailure(String installationId, Instant now, Duration length) {
            Window window = rows.get(installationId);
            Window next = window == null || !window.expiresAt().isAfter(now) ? new Window(1, now.plus(length))
                    : new Window(window.failures() + 1, window.expiresAt());
            rows.put(installationId, next);
            return next.failures();
        }

        @Override
        public synchronized void clear(String installationId) {
            rows.remove(installationId);
        }
    }

    /** Records every audit call as "ACTION:credentialId:detail". */
    public static final class RecordingAudit implements BiometricAudit {
        public final List<String> events = new ArrayList<>();

        @Override
        public void enabled(BiometricCredential credential) {
            events.add("ENABLED:" + credential.credentialId());
        }

        @Override
        public void signedIn(BiometricCredential credential) {
            events.add("SIGNIN:" + credential.credentialId());
        }

        @Override
        public void signInFailed(BiometricCredential credential, String installationId, String failure) {
            events.add("FAILED:" + (credential == null ? "-" : credential.credentialId()) + ":" + failure);
        }

        @Override
        public void revoked(BiometricCredential credential, BiometricRevocationReason reason) {
            events.add("REVOKED:" + credential.credentialId() + ":" + reason);
        }
    }
}
