package com.heuristiq.notification.adapters.out.persistence.jpa;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationMessageEntity;

interface NotificationMessageJpaSpringRepository extends JpaRepository<NotificationMessageEntity, String> {

    Optional<NotificationMessageEntity> findByMessageId(String messageId);

    Optional<NotificationMessageEntity> findFirstByMessageIdAndStatusAndUpdatedAtBefore(
            String messageId, NotificationMessageStatus status, LocalDateTime updatedBefore);

    Page<NotificationMessageEntity> findByCorrelationId(String correlationId, Pageable pageable);

    Page<NotificationMessageEntity> findByStatus(NotificationMessageStatus status, Pageable pageable);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("SELECT e FROM NotificationMessageEntity e WHERE (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationMessageEntity> findAllScoped(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationMessageEntity e WHERE e.correlationId = :correlationId AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationMessageEntity> findByCorrelationIdScoped(@Param("correlationId") String correlationId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationMessageEntity e WHERE e.status = :status AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationMessageEntity> findByStatusScoped(@Param("status") NotificationMessageStatus status,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Transactional
    long deleteByCompletedAtBefore(LocalDateTime cutoff);
}
