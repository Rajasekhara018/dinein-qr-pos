package com.railcab.nms.domain.repository;

import java.time.LocalDateTime;

import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.model.NotificationChannel;

public interface INotificationLogRepository {

    NotificationLog save(NotificationLog log);

    java.util.Optional<NotificationLog> findById(String id);

    PageResult<NotificationLog> findAll(PageQuery pageQuery);

    PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery);

    PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery);

    PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery);

    PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery);

    long countByChannelAndSuccessTrue(NotificationChannel channel);

    long countByChannelAndSuccessFalse(NotificationChannel channel);

    /** Delivery count for one channel and outcome, limited to the organization {@code scope}. */
    long countByChannelAndSuccess(NotificationChannel channel, boolean success, TenantScope scope);

    /** Deletes records older than the cutoff. No-op on adapters that already expire records natively (e.g. Mongo TTL index). */
    void purgeOlderThan(LocalDateTime cutoff);
}
