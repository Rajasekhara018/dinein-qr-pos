package com.railcab.nms.application.usecase;

import com.railcab.nms.domain.repository.TenantScope;
import com.railcab.nms.application.security.TenantAccess;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.railcab.nms.application.dto.NotificationChannelStatsResponse;
import com.railcab.nms.application.port.in.NotificationLogUseCase;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.repository.INotificationLogRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.NotificationChannel;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationLogService implements NotificationLogUseCase {

    private final INotificationLogRepository repository;

    @Override
    public PageResult<NotificationLog> findAll(PageQuery pageQuery) {
        return repository.findAll(scoped(pageQuery));
    }

    @Override
    public PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery) {
        return repository.findByEvent(event.trim().toUpperCase(), scoped(pageQuery));
    }

    @Override
    public PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return repository.findByCorrelationId(correlationId, scoped(pageQuery));
    }

    @Override
    public PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery) {
        return repository.findByChannel(channel, scoped(pageQuery));
    }

    @Override
    public PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery) {
        return repository.findBySentAtBetween(from, to, scoped(pageQuery));
    }

    @Override
    public List<NotificationChannelStatsResponse> stats() {
        TenantScope scope = TenantAccess.scope();
        return List.of(NotificationChannel.values()).stream()
                .map(channel -> new NotificationChannelStatsResponse(
                        channel,
                        repository.countByChannelAndSuccess(channel, true, scope),
                        repository.countByChannelAndSuccess(channel, false, scope)))
                .toList();
    }

    /** Limits every listing to the caller's organization (plus records stored before organizations existed). */
    private static PageQuery scoped(PageQuery pageQuery) {
        return pageQuery.withScope(TenantAccess.scope());
    }
}
