package com.heuristiq.identity.otp;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory {@link OtpStore} with the same optimistic compare-and-save contract as the real adapters. */
class InMemoryOtpStore implements OtpStore {
    private final ConcurrentHashMap<String, OtpChallenge> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<OtpChallenge> find(String key) {
        OtpChallenge row = rows.get(key);
        return Optional.ofNullable(row == null ? null : row.copy());
    }

    @Override
    public synchronized boolean compareAndSave(OtpChallenge challenge) {
        OtpChallenge current = rows.get(challenge.getKey());
        if (challenge.getVersion() == null) {
            if (current != null) {
                return false;
            }
            challenge.setVersion(0L);
        } else {
            if (current == null || !current.getVersion().equals(challenge.getVersion())) {
                return false;
            }
            challenge.setVersion(challenge.getVersion() + 1);
        }
        rows.put(challenge.getKey(), challenge.copy());
        return true;
    }

    OtpChallenge raw(String key) {
        return rows.get(key);
    }
}
