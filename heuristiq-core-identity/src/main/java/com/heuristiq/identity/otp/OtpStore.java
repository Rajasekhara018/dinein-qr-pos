package com.heuristiq.identity.otp;

import java.util.Optional;

/**
 * Persistence port for {@link OtpChallenge}. Implementations only have to provide an optimistic
 * compare-and-save; all OTP rules (hashing, attempts, cooldown, single use) live in
 * {@link OtpChallengeManager} so they behave identically on MongoDB and PostgreSQL.
 */
public interface OtpStore {

    /** Loads the state for {@code key}, including its version, or empty if none is retained. */
    Optional<OtpChallenge> find(String key);

    /**
     * Writes {@code challenge} only if nobody changed it since it was loaded: a challenge with a
     * null version is inserted (and loses if another writer inserted first), otherwise the stored
     * version must still match. On success the challenge's version is advanced.
     *
     * @return false when the write lost a race and the caller must reload and retry
     */
    boolean compareAndSave(OtpChallenge challenge);
}
