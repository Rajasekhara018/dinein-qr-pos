package com.heuristq.dinein.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Native queries because read state lives in the unmapped {@code notification_read} join table. A notification is
 * visible to a user when it belongs to their restaurant AND targets their role or their user id ({@code :userKey}
 * is the id as text).
 */
public interface InAppNotificationRepository extends JpaRepository<InAppNotificationEntity, Long> {

    String VISIBLE = "n.restaurant_id = :restaurantId AND "
            + "((n.audience = 'STAFF_ROLE' AND n.recipient = :role) "
            + "OR (n.audience = 'STAFF_USER' AND n.recipient = :userKey))";
    String UNREAD = "NOT EXISTS (SELECT 1 FROM notification_read r "
            + "WHERE r.notification_id = n.id AND r.staff_user_id = :userId)";

    @Query(value = "SELECT n.* FROM in_app_notification n WHERE " + VISIBLE
            + " AND (:unreadOnly = false OR " + UNREAD + ") ORDER BY n.created_at DESC, n.id DESC",
            countQuery = "SELECT count(*) FROM in_app_notification n WHERE " + VISIBLE
                    + " AND (:unreadOnly = false OR " + UNREAD + ")",
            nativeQuery = true)
    Page<InAppNotificationEntity> findVisible(@Param("restaurantId") long restaurantId, @Param("role") String role,
                                              @Param("userKey") String userKey, @Param("userId") long userId,
                                              @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query(value = "SELECT count(*) FROM in_app_notification n WHERE " + VISIBLE + " AND " + UNREAD,
            nativeQuery = true)
    long countUnread(@Param("restaurantId") long restaurantId, @Param("role") String role,
                     @Param("userKey") String userKey, @Param("userId") long userId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM in_app_notification n WHERE n.id = :id AND " + VISIBLE + ")",
            nativeQuery = true)
    boolean isVisible(@Param("id") long id, @Param("restaurantId") long restaurantId, @Param("role") String role,
                      @Param("userKey") String userKey);

    @Query(value = "SELECT r.notification_id FROM notification_read r "
            + "WHERE r.staff_user_id = :userId AND r.notification_id IN (:ids)", nativeQuery = true)
    List<Long> findReadIds(@Param("userId") long userId, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query(value = "INSERT INTO notification_read (notification_id, staff_user_id, read_at) "
            + "VALUES (:id, :userId, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int markRead(@Param("id") long id, @Param("userId") long userId);

    @Modifying
    @Query(value = "INSERT INTO notification_read (notification_id, staff_user_id, read_at) "
            + "SELECT n.id, :userId, now() FROM in_app_notification n WHERE " + VISIBLE + " AND " + UNREAD
            + " ON CONFLICT DO NOTHING", nativeQuery = true)
    int markAllRead(@Param("restaurantId") long restaurantId, @Param("role") String role,
                    @Param("userKey") String userKey, @Param("userId") long userId);
}
