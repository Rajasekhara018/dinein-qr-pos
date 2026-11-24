package com.railcab.dms.centralaudit.postgres;

import com.railcab.dms.centralaudit.CentralAuditConstants;
import com.railcab.dms.centralaudit.OutboxEntry;
import com.railcab.dms.centralaudit.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Postgres shape of an outbox entry (schema by hbm2ddl like the rest of DMS). Event id is unique. */
@Entity
@Table(name = "dms_audit_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_dms_audit_outbox_event_id", columnNames = "eventId"),
        indexes = {
                @Index(name = "idx_dms_audit_outbox_due", columnList = "status,nextAttemptAt"),
                @Index(name = "idx_dms_audit_outbox_expire", columnList = "expireAt")
        })
class AuditOutboxJpaEntity {

    @Id
    String id;
    @Column(nullable = false)
    String eventId;
    @Column(nullable = false, columnDefinition = "text")
    String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OutboxStatus status;
    int attempts;
    Instant nextAttemptAt;
    Instant lockedUntil;
    String lockOwner;
    @Column(length = CentralAuditConstants.ERROR_COLUMN_LENGTH)
    String lastError;
    Instant createdAt;
    Instant sentAt;
    Instant expireAt;

    static AuditOutboxJpaEntity fromDomain(OutboxEntry e, String id) {
        AuditOutboxJpaEntity d = new AuditOutboxJpaEntity();
        d.id = id;
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
