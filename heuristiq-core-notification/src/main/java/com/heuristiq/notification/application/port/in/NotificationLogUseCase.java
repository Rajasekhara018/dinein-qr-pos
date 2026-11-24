package com.heuristiq.notification.application.port.in;

import java.time.LocalDateTime;
import java.util.List;

import com.heuristiq.notification.application.dto.NotificationChannelStatsResponse;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Port the web adapter depends on instead of the raw repository. */
public interface NotificationLogUseCase {

    PageResult<NotificationLog> findAll(PageQuery pageQuery);

    PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery);

    PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery);

    PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery);

    PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery);

    List<NotificationChannelStatsResponse> stats();
}
