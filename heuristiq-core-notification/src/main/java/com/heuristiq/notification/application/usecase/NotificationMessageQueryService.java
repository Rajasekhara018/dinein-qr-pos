package com.railcab.nms.application.usecase;

import com.railcab.nms.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.railcab.nms.application.port.in.NotificationMessageQueryUseCase;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationMessageQueryService implements NotificationMessageQueryUseCase {

    private final INotificationMessageRepository repository;

    @Override
    public PageResult<NotificationMessage> list(NotificationMessageStatus status, String correlationId, PageQuery requested) {
        PageQuery pageQuery = requested.withScope(TenantAccess.scope());
        if (correlationId != null && !correlationId.isBlank()) {
            return repository.findByCorrelationId(correlationId, pageQuery);
        }
        if (status == null) {
            return repository.findAll(pageQuery);
        }
        return repository.findByStatus(status, pageQuery);
    }
}
