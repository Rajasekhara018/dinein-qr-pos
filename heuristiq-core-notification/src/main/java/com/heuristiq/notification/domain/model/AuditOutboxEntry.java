package com.railcab.nms.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A communication audit event waiting to be pushed to the central audit service. It is the durable
 * hand-off between the send path (which must never wait for, or fail because of, the audit
 * service) and the scheduled drain. {@code eventId} is unique so a re-enqueued event is a no-op.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditOutboxEntry {

    private String id;

    /** Deterministic id the audit service de-duplicates on. */
    private String eventId;

    /** The event as JSON, already free of bodies, OTPs and unmasked recipients. */
    private String payload;

    private AuditOutboxStatus status;

    private int attempts;

    private LocalDateTime nextAttemptAt;

    /** Lease: a drainer owns the row until this instant. */
    private LocalDateTime lockedUntil;

    private String lockOwner;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    /** Row is removed (TTL / purge) after this, whether or not it was sent. */
    private LocalDateTime expireAt;
}
