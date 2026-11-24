package com.heuristiq.documents.centralaudit.mongo;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.centralaudit.AuditOutboxStore;
import com.heuristiq.documents.centralaudit.OutboxEntry;
import com.heuristiq.documents.centralaudit.OutboxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** MongoDB adapter of the audit outbox. The lease is one conditional {@code findAndModify}. */
@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoAuditOutboxStore implements AuditOutboxStore {

    private final MongoTemplate mongoTemplate;

    public MongoAuditOutboxStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean insertIfAbsent(OutboxEntry entry) {
        try {
            mongoTemplate.insert(AuditOutboxMongoDocument.fromDomain(entry));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public List<OutboxEntry> findDue(Instant now, int limit) {
        Query query = new Query(new Criteria().andOperator(
                        Criteria.where("status").is(OutboxStatus.PENDING),
                        Criteria.where("nextAttemptAt").lte(now),
                        new Criteria().orOperator(Criteria.where("lockedUntil").is(null),
                                Criteria.where("lockedUntil").lt(now))))
                .with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, AuditOutboxMongoDocument.class).stream()
                .map(AuditOutboxMongoDocument::toDomain).toList();
    }

    @Override
    public boolean tryLease(String id, String owner, Instant now, Instant leaseUntil) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(id), Criteria.where("status").is(OutboxStatus.PENDING),
                new Criteria().orOperator(Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lt(now))));
        Update update = new Update().set("lockedUntil", leaseUntil).set("lockOwner", owner);
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                AuditOutboxMongoDocument.class) != null;
    }

    @Override
    public void markSent(String id, Instant sentAt) {
        mongoTemplate.updateFirst(byId(id), new Update().set("status", OutboxStatus.SENT).set("sentAt", sentAt)
                .unset("lockedUntil").unset("lockOwner").unset("lastError"), AuditOutboxMongoDocument.class);
    }

    @Override
    public void markFailed(String id, int attempts, Instant nextAttemptAt, String error, boolean dead) {
        mongoTemplate.updateFirst(byId(id), new Update()
                .set("status", dead ? OutboxStatus.DEAD : OutboxStatus.PENDING)
                .set("attempts", attempts).set("nextAttemptAt", nextAttemptAt).set("lastError", error)
                .unset("lockedUntil").unset("lockOwner"), AuditOutboxMongoDocument.class);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return mongoTemplate.count(new Query(Criteria.where("status").is(status)), AuditOutboxMongoDocument.class);
    }

    @Override
    public void purgeExpired(Instant now) {
        // No-op: the TTL index on "expireAt" removes expired entries natively.
    }

    private static Query byId(String id) {
        return new Query(Criteria.where("_id").is(id));
    }
}
