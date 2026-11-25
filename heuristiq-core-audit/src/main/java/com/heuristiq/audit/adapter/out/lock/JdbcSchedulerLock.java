package com.heuristiq.audit.adapter.out.lock;

import com.heuristiq.audit.domain.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link SchedulerLock}. A lease in the database, so a periodic job runs on ONE instance at a time when the service is scaled
 * out. An instance takes the named lease for a while; the others find it taken and skip that round. If
 * the holder dies the lease simply runs out and another instance takes over, so nothing needs cleaning up.
 * Check-and-take is a single {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE}, which PostgreSQL executes
 * atomically, so two instances can never both win. The table is created by migration V2.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "postgres")
public class JdbcSchedulerLock implements SchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(JdbcSchedulerLock.class);

    private final JdbcTemplate jdbc;
    private final String holder;

    /**
     * Creates the lock for this process; the holder id is unique per process.
     *
     * @param jdbc the JDBC access to the service database
     */
    @Autowired
    public JdbcSchedulerLock(JdbcTemplate jdbc) {
        this(jdbc, hostName() + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** Test seam: fixes the holder id. */
    JdbcSchedulerLock(JdbcTemplate jdbc, String holder) {
        this.jdbc = jdbc;
        this.holder = holder;
    }

    /**
     * Tries to take (or renew) the lease {@code name} for {@code ttlMillis}.
     *
     * @param name      lease name, e.g. {@code audit-retention}
     * @param ttlMillis how long the lease lasts if it is not released first; must be positive
     * @return true when this instance now holds the lease and should run the job; false when another
     *         instance holds it. A database problem counts as "not taken", so a job never runs unprotected.
     */
    public boolean tryAcquire(String name, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(SchedulerLockConstants.TTL_NOT_POSITIVE);
        }
        try {
            return jdbc.update(SchedulerLockConstants.ACQUIRE_SQL, name, holder, ttlMillis) == 1;
        } catch (RuntimeException e) {
            log.warn("scheduler.lock.acquire-failed name={} reason={}", name, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Gives the lease back early (the job finished), so the next round, here or on another instance, is
     * not held up. Only the current holder can release; a lease held by someone else is left alone.
     *
     * @param name lease name
     */
    public void release(String name) {
        try {
            jdbc.update(SchedulerLockConstants.RELEASE_SQL, name, holder);
        } catch (RuntimeException e) {
            log.warn("scheduler.lock.release-failed name={} reason={}", name, e.getClass().getSimpleName());
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return SchedulerLockConstants.FALLBACK_HOLDER;
        }
    }
}
