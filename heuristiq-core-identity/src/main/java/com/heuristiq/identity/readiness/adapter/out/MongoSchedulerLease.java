package com.heuristiq.uam.readiness.adapter.out;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.heuristiq.uam.readiness.application.port.SchedulerLease;

/**
 * MongoDB lease in the {@code scheduler_locks} collection. One upsert on {@code _id = name} that only matches when the lease
 * has run out or is already ours; when another instance holds it the upsert would insert a second document with the same id,
 * which the unique {@code _id} refuses (duplicate key), so exactly one caller wins. Expiry uses this instance's clock, so
 * instances must keep their clocks in sync (the lease is minutes long).
 */
public class MongoSchedulerLease implements SchedulerLease {

    private static final Logger log = LoggerFactory.getLogger(MongoSchedulerLease.class);

    private final MongoTemplate mongo;
    private final Clock clock;
    private final String holder;

    public MongoSchedulerLease(MongoTemplate mongo, Clock clock) {
        this(mongo, clock, JdbcSchedulerLease.newHolder());
    }

    MongoSchedulerLease(MongoTemplate mongo, Clock clock, String holder) {
        this.mongo = mongo;
        this.clock = clock;
        this.holder = holder;
    }

    @Override
    public boolean tryAcquire(String name, Duration ttl) {
        Instant now = clock.instant();
        Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(name),
                new Criteria().orOperator(Criteria.where(GovernanceAdapterConstants.LEASE_FIELD_LOCKED_UNTIL).lt(Date.from(now)),
                        Criteria.where(GovernanceAdapterConstants.LEASE_FIELD_HOLDER).is(holder))));
        Update update = new Update().set(GovernanceAdapterConstants.LEASE_FIELD_HOLDER, holder)
                .set(GovernanceAdapterConstants.LEASE_FIELD_LOCKED_UNTIL, Date.from(now.plus(ttl)));
        try {
            return mongo.findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                    org.bson.Document.class, GovernanceAdapterConstants.LEASE_COLLECTION) != null;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (RuntimeException e) {
            log.warn("scheduler.lease.acquire-failed name={} reason={}", name, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void release(String name) {
        try {
            Query query = Query.query(Criteria.where("_id").is(name).and(GovernanceAdapterConstants.LEASE_FIELD_HOLDER).is(holder));
            mongo.updateFirst(query, new Update().set(GovernanceAdapterConstants.LEASE_FIELD_LOCKED_UNTIL,
                    Date.from(clock.instant().minusSeconds(1))), GovernanceAdapterConstants.LEASE_COLLECTION);
        } catch (RuntimeException e) {
            log.warn("scheduler.lease.release-failed name={} reason={}", name, e.getClass().getSimpleName());
        }
    }
}
