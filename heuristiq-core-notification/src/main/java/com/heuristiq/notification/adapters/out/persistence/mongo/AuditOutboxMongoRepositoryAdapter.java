package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;

/**
 * Mongo implementation of {@link IAuditOutboxRepository}. The lease is a single conditional
 * {@code findAndModify}, so only one NMS instance can take a given entry.
 */
@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class AuditOutboxMongoRepositoryAdapter implements IAuditOutboxRepository {

    private final MongoTemplate mongoTemplate;

    AuditOutboxMongoRepositoryAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean insertIfAbsent(AuditOutboxEntry entry) {
        try {
            mongoTemplate.insert(toDocument(entry));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public List<AuditOutboxEntry> findDue(LocalDateTime now, int limit) {
        Query query = new Query(notLeased(now)).with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, AuditOutboxDocument.class).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean tryLease(String id, String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(id), Criteria.where("status").is(AuditOutboxStatus.PENDING),
                new Criteria().orOperator(Criteria.where("lockedUntil").is(null),
                        Criteria.where("lockedUntil").lt(now))));
        Update update = new Update().set("lockedUntil", leaseUntil).set("lockOwner", owner);
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                AuditOutboxDocument.class) != null;
    }

    @Override
    public void markSent(String id, LocalDateTime sentAt) {
        mongoTemplate.updateFirst(byId(id), new Update().set("status", AuditOutboxStatus.SENT).set("sentAt", sentAt)
                .unset("lockedUntil").unset("lockOwner").unset("lastError"), AuditOutboxDocument.class);
    }

    @Override
    public void markFailed(String id, int attempts, LocalDateTime nextAttemptAt, String error, boolean dead) {
        mongoTemplate.updateFirst(byId(id), new Update()
                .set("status", dead ? AuditOutboxStatus.DEAD : AuditOutboxStatus.PENDING)
                .set("attempts", attempts).set("nextAttemptAt", nextAttemptAt).set("lastError", error)
                .unset("lockedUntil").unset("lockOwner"), AuditOutboxDocument.class);
    }

    @Override
    public long countByStatus(AuditOutboxStatus status) {
        return mongoTemplate.count(new Query(Criteria.where("status").is(status)), AuditOutboxDocument.class);
    }

    @Override
    public void purgeExpired(LocalDateTime now) {
        // No-op: the TTL index on "expireAt" (see AuditOutboxIndexConfig) removes expired entries natively.
    }

    private static Criteria notLeased(LocalDateTime now) {
        return new Criteria().andOperator(
                Criteria.where("status").is(AuditOutboxStatus.PENDING),
                Criteria.where("nextAttemptAt").lte(now),
                new Criteria().orOperator(Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lt(now)));
    }

    private static Query byId(String id) {
        return new Query(Criteria.where("_id").is(id));
    }

    private AuditOutboxEntry toDomain(AuditOutboxDocument d) {
        return AuditOutboxEntry.builder()
                .id(d.getId())
                .eventId(d.getEventId())
                .payload(d.getPayload())
                .status(d.getStatus())
                .attempts(d.getAttempts())
                .nextAttemptAt(d.getNextAttemptAt())
                .lockedUntil(d.getLockedUntil())
                .lockOwner(d.getLockOwner())
                .lastError(d.getLastError())
                .createdAt(d.getCreatedAt())
                .sentAt(d.getSentAt())
                .expireAt(d.getExpireAt())
                .build();
    }

    private AuditOutboxDocument toDocument(AuditOutboxEntry e) {
        return AuditOutboxDocument.builder()
                .id(e.getId())
                .eventId(e.getEventId())
                .payload(e.getPayload())
                .status(e.getStatus())
                .attempts(e.getAttempts())
                .nextAttemptAt(e.getNextAttemptAt())
                .lockedUntil(e.getLockedUntil())
                .lockOwner(e.getLockOwner())
                .lastError(e.getLastError())
                .createdAt(e.getCreatedAt())
                .sentAt(e.getSentAt())
                .expireAt(e.getExpireAt())
                .build();
    }
}
