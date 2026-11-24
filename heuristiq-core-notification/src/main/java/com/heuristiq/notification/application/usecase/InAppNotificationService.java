package com.railcab.nms.application.usecase;

import com.railcab.nms.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.railcab.nms.application.port.in.InAppNotificationUseCase;
import com.railcab.nms.domain.model.InAppNotification;
import com.railcab.nms.domain.repository.IInAppNotificationRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InAppNotificationService implements InAppNotificationUseCase {

    private final IInAppNotificationRepository repository;

    @Override
    public PageResult<InAppNotification> list(String userId, PageQuery pageQuery) {
        return repository.findByUserId(userId, pageQuery.withScope(TenantAccess.scope()));
    }

    @Override
    public long unreadCount(String userId) {
        return repository.countUnreadByUserId(userId, TenantAccess.scope());
    }

    @Override
    public void markAsRead(String id, String userId) {
        repository.markAsRead(id, userId, TenantAccess.scope());
    }

    @Override
    public void markAllAsRead(String userId) {
        repository.markAllAsRead(userId, TenantAccess.scope());
    }
}
