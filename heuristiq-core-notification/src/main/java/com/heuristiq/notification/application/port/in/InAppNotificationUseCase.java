package com.railcab.nms.application.port.in;

import com.railcab.nms.domain.model.InAppNotification;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/** Port the web adapter depends on instead of the raw repository. */
public interface InAppNotificationUseCase {

    PageResult<InAppNotification> list(String userId, PageQuery pageQuery);

    long unreadCount(String userId);

    /** Scoped by userId so a caller can't mark another user's notification as read. */
    void markAsRead(String id, String userId);

    void markAllAsRead(String userId);
}
