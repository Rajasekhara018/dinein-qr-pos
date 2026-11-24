package com.railcab.nms.domain.repository;

import com.railcab.nms.domain.model.InAppNotification;

public interface IInAppNotificationRepository {

    InAppNotification save(InAppNotification notification);

    /** Full history for the user, newest first — not filtered to unread. */
    PageResult<InAppNotification> findByUserId(String userId, PageQuery pageQuery);

    PageResult<InAppNotification> findUnreadByUserId(String userId, PageQuery pageQuery);

    /** Unread count; the organization of the read comes from {@code scope}. */
    long countUnreadByUserId(String userId, TenantScope scope);

    default long countUnreadByUserId(String userId) {
        return countUnreadByUserId(userId, TenantScope.UNRESTRICTED);
    }

    /**
     * Scoped by userId so a caller can't mark another user's notification as read, and by {@code scope} so a
     * notification of another organization is left alone.
     */
    void markAsRead(String id, String userId, TenantScope scope);

    default void markAsRead(String id, String userId) {
        markAsRead(id, userId, TenantScope.UNRESTRICTED);
    }

    void markAllAsRead(String userId, TenantScope scope);

    default void markAllAsRead(String userId) {
        markAllAsRead(userId, TenantScope.UNRESTRICTED);
    }
}
