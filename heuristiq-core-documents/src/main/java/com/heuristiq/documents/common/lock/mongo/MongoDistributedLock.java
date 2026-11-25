package com.heuristiq.documents.common.lock.mongo;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.common.lock.DistributedLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoDistributedLock implements DistributedLock {

    private final MongoTemplate mongoTemplate;

    public MongoDistributedLock(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean tryAcquire(String key, Duration lease, String owner) {
        Instant now = Instant.now();
        Criteria criteria = new Criteria().andOperator(
                Criteria.where(MongoLockConstants.FIELD_ID).is(key),
                new Criteria().orOperator(Criteria.where(MongoLockConstants.FIELD_LOCKED_UNTIL).exists(false),
                        Criteria.where(MongoLockConstants.FIELD_LOCKED_UNTIL).lt(now))
        );
        Update update = Update.update(MongoLockConstants.FIELD_LOCKED_UNTIL, now.plus(lease)).set(MongoLockConstants.FIELD_OWNER, owner);
        try {
            JobLock acquired = mongoTemplate.findAndModify(
                    Query.query(criteria),
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    JobLock.class,
                    MongoLockConstants.LOCK_COLLECTION
            );
            return acquired != null && owner.equals(acquired.owner);
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void release(String key, String owner) {
        Query query = Query.query(Criteria.where(MongoLockConstants.FIELD_ID).is(key).and(MongoLockConstants.FIELD_OWNER).is(owner));
        mongoTemplate.updateFirst(query, Update.update(MongoLockConstants.FIELD_LOCKED_UNTIL, Instant.EPOCH), MongoLockConstants.LOCK_COLLECTION);
    }

    private static final class JobLock {
        @Id
        private String id;
        private Instant lockedUntil;
        private String owner;
    }
}
