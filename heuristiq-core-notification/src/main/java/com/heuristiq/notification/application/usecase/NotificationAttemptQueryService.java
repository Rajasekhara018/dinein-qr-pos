package com.railcab.nms.application.usecase;

import com.railcab.nms.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.railcab.nms.application.port.in.NotificationAttemptQueryUseCase;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.repository.INotificationAttemptRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

import lombok.RequiredArgsConstructor;

/** Read side of the per-attempt records; caps the page size so a single call stays bounded. */
@Service
@RequiredArgsConstructor
public class NotificationAttemptQueryService implements NotificationAttemptQueryUseCase {

    /** Largest page a caller can ask for. */
    static final int MAX_PAGE_SIZE = 200;

    private final INotificationAttemptRepository repository;

    @Override
    public PageResult<NotificationAttempt> findByMessageId(String messageId, PageQuery pageQuery) {
        return repository.findByMessageId(messageId, capped(pageQuery));
    }

    @Override
    public PageResult<NotificationAttempt> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return repository.findByCorrelationId(correlationId, capped(pageQuery));
    }

    private static PageQuery capped(PageQuery query) {
        return new PageQuery(Math.max(0, query.page()), Math.min(Math.max(1, query.size()), MAX_PAGE_SIZE),
                TenantAccess.scope());
    }
}
