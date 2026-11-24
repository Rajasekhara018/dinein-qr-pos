package com.heuristiq.uam.biometric.application.port;

import com.heuristiq.uam.biometric.domain.BiometricChallenge;
import java.time.Instant;
import java.util.Optional;

/**
 * Shared storage of sign-in challenges (UAM's own database, so every UAM instance sees the same challenges). A challenge is
 * consumed atomically: of two concurrent verifications of one challenge exactly one gets it.
 */
public interface BiometricChallengeStore {

    void save(BiometricChallenge challenge);

    /**
     * Marks the challenge used and returns it, only when it exists, is unused and has not expired at {@code now}; empty
     * otherwise (unknown, expired or already used, deliberately not saying which).
     */
    Optional<BiometricChallenge> consume(String challengeId, Instant now);
}
