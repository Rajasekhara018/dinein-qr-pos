package com.heuristiq.notification.adapters.out.persistence.jpa;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationLogEntity;

interface NotificationLogJpaSpringRepository extends JpaRepository<NotificationLogEntity, String> {

    Page<NotificationLogEntity> findByEvent(String event, Pageable pageable);

    Page<NotificationLogEntity> findByCorrelationId(String correlationId, Pageable pageable);

    Page<NotificationLogEntity> findByChannel(NotificationChannel channel, Pageable pageable);

    Page<NotificationLogEntity> findBySentAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByChannelAndSuccessTrue(NotificationChannel channel);

    long countByChannelAndSuccessFalse(NotificationChannel channel);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("SELECT e FROM NotificationLogEntity e WHERE (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationLogEntity> findAllScoped(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationLogEntity e WHERE e.event = :event AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationLogEntity> findByEventScoped(@Param("event") String event, @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationLogEntity e WHERE e.correlationId = :correlationId AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationLogEntity> findByCorrelationIdScoped(@Param("correlationId") String correlationId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationLogEntity e WHERE e.channel = :channel AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationLogEntity> findByChannelScoped(@Param("channel") NotificationChannel channel,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationLogEntity e WHERE e.sentAt BETWEEN :from AND :to AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    Page<NotificationLogEntity> findBySentAtBetweenScoped(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT COUNT(e) FROM NotificationLogEntity e WHERE e.channel = :channel AND e.success = :success AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    long countByChannelAndSuccessScoped(@Param("channel") NotificationChannel channel, @Param("success") boolean success,
            @Param("tenantId") String tenantId);

    @Transactional
    long deleteBySentAtBefore(LocalDateTime cutoff);
}
