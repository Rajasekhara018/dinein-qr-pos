package com.railcab.nms.application.port.in;

import java.time.LocalDateTime;
import java.util.List;

import com.railcab.nms.application.dto.NotificationChannelStatsResponse;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/** Port the web adapter depends on instead of the raw repository. */
public interface NotificationLogUseCase {

    PageResult<NotificationLog> findAll(PageQuery pageQuery);

    PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery);

    PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery);

    PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery);

    PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery);

    List<NotificationChannelStatsResponse> stats();
}
