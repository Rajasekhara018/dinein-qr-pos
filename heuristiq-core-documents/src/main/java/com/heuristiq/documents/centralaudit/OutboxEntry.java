package com.railcab.dms.centralaudit;

import java.time.Instant;

/**
 * An audit event waiting to be pushed to the central audit service: the durable hand-off between the request path
 * (which never waits for, or fails because of, the audit service) and the scheduled drain.
 *
 * @param id            store id
 * @param eventId       the id the audit service de-duplicates on; unique in the outbox
 * @param payload       the event as JSON
 * @param status        PENDING, SENT or DEAD
 * @param attempts      failed pushes so far
 * @param nextAttemptAt earliest next push
 * @param lockedUntil   a drainer owns the row until this instant
 * @param lockOwner     the drainer instance that owns it
 * @param lastError     short reason of the last failed push
 * @param createdAt     when it was queued
 * @param sentAt        when it was accepted
 * @param expireAt      the row is removed after this, whether or not it was sent
 */
public record OutboxEntry(String id, String eventId, String payload, OutboxStatus status, int attempts,
                          Instant nextAttemptAt, Instant lockedUntil, String lockOwner, String lastError,
                          Instant createdAt, Instant sentAt, Instant expireAt) {
}
