package com.railcab.dms.centralaudit.mongo;

import com.railcab.dms.centralaudit.OutboxEntry;
import com.railcab.dms.centralaudit.OutboxStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo shape of an outbox entry. Indexes: unique event id (idempotent queueing), the drain lookup, and a TTL on
 * {@code expireAt} that removes expired entries natively.
 */
@Document(collection = "dms_audit_outbox")
@CompoundIndex(name = "idx_due", def = "{'status': 1, 'nextAttemptAt': 1}")
class AuditOutboxMongoDocument {

    @Id
    String id;
    @Indexed(unique = true)
    String eventId;
    String payload;
    OutboxStatus status;
    int attempts;
    Instant nextAttemptAt;
    Instant lockedUntil;
    String lockOwner;
    String lastError;
    Instant createdAt;
    Instant sentAt;
    @Indexed(expireAfter = "0s")
    Instant expireAt;

    static AuditOutboxMongoDocument fromDomain(OutboxEntry e) {
        AuditOutboxMongoDocument d = new AuditOutboxMongoDocument();
        d.id = e.id();
        d.eventId = e.eventId();
        d.payload = e.payload();
        d.status = e.status();
        d.attempts = e.attempts();
        d.nextAttemptAt = e.nextAttemptAt();
        d.lockedUntil = e.lockedUntil();
        d.lockOwner = e.lockOwner();
        d.lastError = e.lastError();
        d.createdAt = e.createdAt();
        d.sentAt = e.sentAt();
        d.expireAt = e.expireAt();
        return d;
    }

    OutboxEntry toDomain() {
        return new OutboxEntry(id, eventId, payload, status, attempts, nextAttemptAt, lockedUntil, lockOwner, lastError,
                createdAt, sentAt, expireAt);
    }
}
