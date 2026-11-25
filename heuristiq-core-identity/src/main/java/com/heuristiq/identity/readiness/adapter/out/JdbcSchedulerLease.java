package com.heuristiq.identity.readiness.adapter.out;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.heuristiq.identity.readiness.application.port.SchedulerLease;

/**
 * PostgreSQL lease in the {@code scheduler_locks} table (migration V17). Check-and-take is one atomic
 * {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE}, so two instances can never both win; both timestamps come from the
 * database clock. A database problem counts as "not taken".
 */
public class JdbcSchedulerLease implements SchedulerLease {

    private static final Logger log = LoggerFactory.getLogger(JdbcSchedulerLease.class);

    private final JdbcTemplate jdbc;
    private final String holder;

    public JdbcSchedulerLease(JdbcTemplate jdbc) {
        this(jdbc, newHolder());
    }

    /** Test seam: fixes the holder id. */
    JdbcSchedulerLease(JdbcTemplate jdbc, String holder) {
        this.jdbc = jdbc;
        this.holder = holder;
    }

    @Override
    public boolean tryAcquire(String name, Duration ttl) {
        try {
            return jdbc.update(GovernanceAdapterConstants.LEASE_ACQUIRE_SQL, name, holder, ttl.toMillis()) == 1;
        } catch (RuntimeException e) {
            log.warn("scheduler.lease.acquire-failed name={} reason={}", name, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void release(String name) {
        try {
            jdbc.update(GovernanceAdapterConstants.LEASE_RELEASE_SQL, name, holder);
        } catch (RuntimeException e) {
            log.warn("scheduler.lease.release-failed name={} reason={}", name, e.getClass().getSimpleName());
        }
    }

    /** A holder id unique to this process. */
    static String newHolder() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException e) {
            host = GovernanceAdapterConstants.FALLBACK_HOLDER;
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
