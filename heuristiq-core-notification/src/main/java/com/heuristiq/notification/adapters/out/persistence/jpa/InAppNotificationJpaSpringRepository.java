package com.heuristiq.notification.adapters.out.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heuristiq.notification.adapters.out.persistence.jpa.InAppNotificationEntity;

interface InAppNotificationJpaSpringRepository extends JpaRepository<InAppNotificationEntity, String> {

    Page<InAppNotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<InAppNotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("SELECT e FROM InAppNotificationEntity e WHERE e.userId = :userId AND (e.tenantId IS NULL OR e.tenantId = :tenantId) ORDER BY e.createdAt DESC")
    Page<InAppNotificationEntity> findByUserIdScoped(@Param("userId") String userId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM InAppNotificationEntity e WHERE e.userId = :userId AND e.read = false AND (e.tenantId IS NULL OR e.tenantId = :tenantId) "
            + "ORDER BY e.createdAt DESC")
    Page<InAppNotificationEntity> findUnreadByUserIdScoped(@Param("userId") String userId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT COUNT(e) FROM InAppNotificationEntity e WHERE e.userId = :userId AND e.read = false AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    long countUnreadByUserIdScoped(@Param("userId") String userId, @Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE InAppNotificationEntity e SET e.read = true WHERE e.id = :id AND e.userId = :userId AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    int markAsReadScoped(@Param("id") String id, @Param("userId") String userId, @Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE InAppNotificationEntity e SET e.read = true WHERE e.userId = :userId AND e.read = false AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    int markAllAsReadScoped(@Param("userId") String userId, @Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE InAppNotificationEntity e SET e.read = true WHERE e.id = :id AND e.userId = :userId")
    int markAsRead(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("UPDATE InAppNotificationEntity e SET e.read = true WHERE e.userId = :userId AND e.read = false")
    int markAllAsRead(@Param("userId") String userId);
}
