package com.heuristiq.identity.centralaudit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
 * MongoDB outbox (collection {@code central_audit_outbox}, same shape as the PostgreSQL table; the document id is the event
 * id). Rows are leased one at a time with an atomic find-and-modify, so two drainers never receive the same row.
 */
public class MongoCentralAuditOutbox implements CentralAuditOutbox {

    private static final Logger log = LoggerFactory.getLogger(MongoCentralAuditOutbox.class);
    private static final String COLLECTION = "central_audit_outbox";
    private static final String TENANT = "tenantId";
    private static final String PAYLOAD = "payload";
    private static final String ATTEMPTS = "attempts";
    private static final String CREATED = "createdAt";
    private static final String NEXT = "nextAttemptAt";
    private static final String LOCKED_UNTIL = "lockedUntil";
    private static final String LOCK_TOKEN = "lockToken";

    private final MongoTemplate mongo;

    public MongoCentralAuditOutbox(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(COLLECTION).ensureIndex(new Index().on(NEXT, Sort.Direction.ASC).named("idx_due"));
            mongo.indexOps(COLLECTION).ensureIndex(new Index().on(CREATED, Sort.Direction.ASC).named("idx_created"));
        } catch (RuntimeException e) {
            log.warn("uam.audit.outbox.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void add(String eventId, String tenantId, String payload, Instant now) {
        Document doc = new Document("_id", eventId).append(TENANT, tenantId).append(PAYLOAD, payload).append(ATTEMPTS, 0)
                .append(CREATED, Date.from(now)).append(NEXT, Date.from(now));
        try {
            mongo.insert(doc, COLLECTION);
        } catch (DuplicateKeyException e) {
            // already stored: the ingest is idempotent on eventId
        }
    }

    @Override
    public List<Entry> claim(int max, Instant now, Duration lease) {
        List<Entry> claimed = new ArrayList<>();
        Date nowDate = Date.from(now);
        for (int i = 0; i < max; i++) {
            Query due = Query.query(Criteria.where(NEXT).lte(nowDate).orOperator(Criteria.where(LOCKED_UNTIL).is(null),
                    Criteria.where(LOCKED_UNTIL).lt(nowDate))).with(Sort.by(CREATED, "_id"));
            Update take = new Update().set(LOCKED_UNTIL, Date.from(now.plus(lease))).set(LOCK_TOKEN, UUID.randomUUID().toString());
            Document doc = mongo.findAndModify(due, take, FindAndModifyOptions.options().returnNew(true), Document.class, COLLECTION);
            if (doc == null) {
                break;
            }
            claimed.add(new Entry(doc.getString("_id"), doc.getString(TENANT), doc.getString(PAYLOAD),
                    doc.getInteger(ATTEMPTS, 0)));
        }
        return claimed;
    }

    @Override
    public void delete(List<String> eventIds) {
        if (!eventIds.isEmpty()) {
            mongo.remove(Query.query(Criteria.where("_id").in(eventIds)), COLLECTION);
        }
    }

    @Override
    public void reschedule(String eventId, int attempts, Instant nextAttemptAt) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(eventId)), new Update().set(ATTEMPTS, attempts)
                .set(NEXT, Date.from(nextAttemptAt)).unset(LOCKED_UNTIL).unset(LOCK_TOKEN), COLLECTION);
    }

    @Override
    public long count() {
        return mongo.getCollection(COLLECTION).estimatedDocumentCount();
    }

    @Override
    public int deleteOldest(Instant olderThan, int max) {
        Query oldest = Query.query(Criteria.where(CREATED).lt(Date.from(olderThan))).with(Sort.by(CREATED, "_id")).limit(max);
        List<String> ids = new ArrayList<>();
        for (Document d : mongo.find(oldest, Document.class, COLLECTION)) {
            ids.add(d.getString("_id"));
        }
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) mongo.remove(Query.query(Criteria.where("_id").in(ids)), COLLECTION).getDeletedCount();
    }
}
