package com.heuristiq.identity.biometric.adapter.out;

import static com.heuristiq.identity.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.identity.biometric.application.port.BiometricAttemptStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * MongoDB counter of failed biometric sign-ins (collection {@code biometric_signin_attempts}, one document per installation).
 * A failure inside the open window is an atomic {@code $inc}; when no window is open the ended one is removed and a new one
 * inserted, and an insert that loses to a concurrent one counts on the winner's window instead. A TTL index removes ended
 * windows.
 */
public class MongoBiometricAttemptStore implements BiometricAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(MongoBiometricAttemptStore.class);

    /** Rounds of "increment, else open a window" before giving up on a pathological race. */
    private static final int MAX_ROUNDS = 3;

    private final MongoTemplate mongo;

    public MongoBiometricAttemptStore(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(ATTEMPTS_COLLECTION).ensureIndex(new Index().on(FIELD_EXPIRES_AT, Sort.Direction.ASC)
                    .expire(TTL_AT_EXPIRY_SECONDS).named(INDEX_ATTEMPTS_TTL));
        } catch (RuntimeException e) {
            log.warn("biometric.attempts.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public int failures(String installationId, Instant now) {
        Document found = mongo.findOne(openWindow(installationId, now), Document.class, ATTEMPTS_COLLECTION);
        return found == null ? 0 : found.getInteger(FIELD_FAILURES, 0);
    }

    @Override
    public int recordFailure(String installationId, Instant now, Duration window) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Document counted = mongo.findAndModify(openWindow(installationId, now), new Update().inc(FIELD_FAILURES, 1),
                    FindAndModifyOptions.options().returnNew(true), Document.class, ATTEMPTS_COLLECTION);
            if (counted != null) {
                return counted.getInteger(FIELD_FAILURES, 1);
            }
            mongo.remove(Query.query(Criteria.where(FIELD_ID).is(installationId).and(FIELD_EXPIRES_AT).lte(Date.from(now))),
                    ATTEMPTS_COLLECTION);
            try {
                mongo.insert(new Document()
                        .append(FIELD_ID, installationId)
                        .append(FIELD_FAILURES, 1)
                        .append(FIELD_WINDOW_STARTED_AT, Date.from(now))
                        .append(FIELD_EXPIRES_AT, Date.from(now.plus(window))), ATTEMPTS_COLLECTION);
                return 1;
            } catch (DuplicateKeyException concurrentFirstFailure) {
                // Another instance opened the window between the two steps: count on it in the next round.
            }
        }
        log.warn("biometric.attempts.count-race-unresolved");
        return failures(installationId, now);
    }

    @Override
    public void clear(String installationId) {
        mongo.remove(Query.query(Criteria.where(FIELD_ID).is(installationId)), ATTEMPTS_COLLECTION);
    }

    private static Query openWindow(String installationId, Instant now) {
        return Query.query(Criteria.where(FIELD_ID).is(installationId).and(FIELD_EXPIRES_AT).gt(Date.from(now)));
    }
}
