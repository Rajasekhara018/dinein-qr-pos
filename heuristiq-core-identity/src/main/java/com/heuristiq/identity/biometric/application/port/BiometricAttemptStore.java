package com.heuristiq.uam.biometric.application.port;

import java.time.Duration;
import java.time.Instant;

/**
 * Shared counter of failed biometric sign-ins per installation, over a fixed window that starts at the first failure.
 * Increments are atomic, so concurrent failures from several UAM instances are all counted.
 */
public interface BiometricAttemptStore {

    /** Failures counted in the window that is open at {@code now} (0 when none is open). */
    int failures(String installationId, Instant now);

    /**
     * Counts one more failure and returns the new count. A window that has ended is replaced by a new one starting at
     * {@code now} and lasting {@code window}.
     */
    int recordFailure(String installationId, Instant now, Duration window);

    /** Forgets the installation's failures (after a successful sign-in). */
    void clear(String installationId);
}
