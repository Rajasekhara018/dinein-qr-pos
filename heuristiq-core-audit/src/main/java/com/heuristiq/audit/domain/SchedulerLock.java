package com.railcab.audit.domain;

import com.railcab.audit.adapter.out.lock.JdbcSchedulerLock;
import com.railcab.audit.adapter.out.lock.MongoSchedulerLock;

/**
 * A named lease so a periodic job runs on ONE instance at a time when the service is scaled out. An
 * instance takes the lease for a while; the others find it taken and skip that round. If the holder
 * dies the lease runs out and another instance takes over, so nothing needs cleaning up.
 *
 * <p>Implementations: {@link JdbcSchedulerLock} (PostgreSQL) and {@code MongoSchedulerLock} (MongoDB),
 * chosen by {@code railcab.audit.persistence.type}.
 */
public interface SchedulerLock {

    /**
     * Tries to take (or renew) the lease {@code name} for {@code ttlMillis}.
     *
     * @param name      lease name, e.g. {@code audit-retention}
     * @param ttlMillis how long the lease lasts if it is not released first; must be positive
     * @return true when this instance now holds the lease and should run the job; false when another
     *         instance holds it. A database problem counts as "not taken", so a job never runs unprotected.
     */
    boolean tryAcquire(String name, long ttlMillis);

    /**
     * Gives the lease back early (the job finished), so the next round, here or on another instance, is
     * not held up. Only the current holder can release; a lease held by someone else is left alone.
     *
     * @param name lease name
     */
    void release(String name);
}
