package com.heuristiq.notification.application.usecase;

import com.heuristiq.notification.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.heuristiq.notification.application.port.in.NotificationMessageQueryUseCase;
import com.heuristiq.notification.domain.model.NotificationMessage;
import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.domain.repository.INotificationMessageRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

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
