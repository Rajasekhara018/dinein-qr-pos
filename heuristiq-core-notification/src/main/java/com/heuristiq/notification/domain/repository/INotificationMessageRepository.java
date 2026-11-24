package com.railcab.nms.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationMessageStatus;

public interface INotificationMessageRepository {

    /** Throws {@link org.springframework.dao.DataIntegrityViolationException} if messageId already exists. */
    NotificationMessage insert(NotificationMessage message);

    NotificationMessage save(NotificationMessage message);

    Optional<NotificationMessage> findByMessageId(String messageId);

    Optional<NotificationMessage> findFirstByMessageIdAndStatusAndUpdatedAtBefore(
            String messageId, NotificationMessageStatus status, LocalDateTime updatedBefore);

    PageResult<NotificationMessage> findAll(PageQuery pageQuery);

    PageResult<NotificationMessage> findByCorrelationId(String correlationId, PageQuery pageQuery);

    PageResult<NotificationMessage> findByStatus(NotificationMessageStatus status, PageQuery pageQuery);

    /** Deletes records older than the cutoff. No-op on adapters that already expire records natively (e.g. Mongo TTL index). */
    void purgeOlderThan(LocalDateTime cutoff);
}
