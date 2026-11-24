package com.heuristiq.notification.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.heuristiq.notification.domain.model.NotificationAttempt;

/** Port for per-attempt delivery records. */
public interface INotificationAttemptRepository {

    /** Inserts or updates; an update on a stale {@code version} fails with an optimistic-lock exception. */
    NotificationAttempt save(NotificationAttempt attempt);

    Optional<NotificationAttempt> findById(String id);

    /** Attempt a provider receipt refers to; the most recent when a provider reuses an id. */
    Optional<NotificationAttempt> findLatestByProviderMessageId(String providerMessageId);

    /** All attempts of one message, oldest first, bounded by {@code pageQuery}. */
    PageResult<NotificationAttempt> findByMessageId(String messageId, PageQuery pageQuery);

    /** All attempts sharing a correlation id, oldest first, bounded by {@code pageQuery}. */
    PageResult<NotificationAttempt> findByCorrelationId(String correlationId, PageQuery pageQuery);

    /** Attempts of one summary log row, by attempt number. */
    List<NotificationAttempt> findByLogId(String logId);

    /** Deletes records older than the cutoff. No-op on adapters that expire natively (Mongo TTL). */
    void purgeOlderThan(LocalDateTime cutoff);
}
