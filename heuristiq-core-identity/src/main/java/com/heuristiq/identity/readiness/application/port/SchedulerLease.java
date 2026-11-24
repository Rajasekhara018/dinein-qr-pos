package com.heuristiq.uam.readiness.application.port;

import java.time.Duration;

/**
 * A named lease so a periodic job runs on ONE UAM instance at a time. It runs out by itself if the holder dies. A storage
 * problem counts as "not taken", so a job never runs unprotected.
 */
public interface SchedulerLease {

    /** Takes (or renews) the lease for {@code ttl}; false when another instance holds it. */
    boolean tryAcquire(String name, Duration ttl);

    /** Gives the lease back early; only the current holder can. */
    void release(String name);
}
