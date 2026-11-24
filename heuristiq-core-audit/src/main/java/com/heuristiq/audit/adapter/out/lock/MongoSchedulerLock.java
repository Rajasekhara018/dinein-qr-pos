package com.heuristiq.audit.adapter.out.lock;

import static com.heuristiq.audit.adapter.out.persistence.PersistenceConstants.*;
import com.heuristiq.audit.domain.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/**
 * MongoDB implementation of {@link SchedulerLock}. A lease document per lock name in the
 * {@code scheduler_locks} collection ({@code _id} = name, fields {@code holder} and {@code lockedUntil}).
 * Take-or-renew is ONE atomic {@code findAndModify} with upsert that matches the document only when the
 * lease has run out or is already ours. When another instance holds a live lease the filter matches
 * nothing, the upsert tries to insert the same {@code _id} and fails with a duplicate-key error, which
 * means "not acquired". Expiry is judged on the clock of the instance asking, so keep instance clocks in sync.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoSchedulerLock implements SchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(MongoSchedulerLock.class);

    private final MongoTemplate mongo;
    private final String holder;
    private final Clock clock;

    /**
     * Creates the lock for this process; the holder id is unique per process.
     *
     * @param mongo the Mongo access to the service database
     * @param clock time source for lease expiry
     */
    @Autowired
    public MongoSchedulerLock(MongoTemplate mongo, Clock clock) {
        this(mongo, clock, hostName() + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** Test seam: fixes the holder id. */
    public MongoSchedulerLock(MongoTemplate mongo, Clock clock, String holder) {
        this.mongo = mongo;
        this.clock = clock;
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
    @Override
    public boolean tryAcquire(String name, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(SchedulerLockConstants.TTL_NOT_POSITIVE);
        }
        try {
            long now = clock.millis();
            Query query = new Query(Criteria.where(SchedulerLockConstants.ID_FIELD).is(name).orOperator(
                    Criteria.where(SchedulerLockConstants.LOCKED_UNTIL_FIELD).lt(new Date(now)),
                    Criteria.where(SchedulerLockConstants.HOLDER_FIELD).is(holder)));
            Update update = new Update().set(SchedulerLockConstants.HOLDER_FIELD, holder).set(SchedulerLockConstants.LOCKED_UNTIL_FIELD, new Date(now + ttlMillis));
            Lease lease = mongo.findAndModify(query, update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), Lease.class, LOCKS_COLLECTION);
            return lease != null && holder.equals(lease.holder);
        } catch (DuplicateKeyException e) {
            return false;
        } catch (RuntimeException e) {
            log.warn("scheduler.lock.acquire-failed name={} reason={}", name, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Gives the lease back early; only the current holder can release, a lease held by someone else is left alone.
     *
     * @param name lease name
     */
    @Override
    public void release(String name) {
        try {
            Query query = new Query(Criteria.where(SchedulerLockConstants.ID_FIELD).is(name).and(SchedulerLockConstants.HOLDER_FIELD).is(holder));
            mongo.updateFirst(query, new Update().set(SchedulerLockConstants.LOCKED_UNTIL_FIELD, new Date(clock.millis() - SchedulerLockConstants.RELEASE_BACKDATE_MS)),
                    LOCKS_COLLECTION);
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

    /** Lease document. */
    private static final class Lease {
        @org.springframework.data.annotation.Id
        String id;
        String holder;
        Date lockedUntil;
    }
}
