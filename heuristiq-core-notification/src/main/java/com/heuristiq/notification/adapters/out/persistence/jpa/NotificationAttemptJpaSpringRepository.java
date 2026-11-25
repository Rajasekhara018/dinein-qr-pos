package com.heuristiq.notification.adapters.out.persistence.jpa;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface NotificationAttemptJpaSpringRepository extends JpaRepository<NotificationAttemptEntity, String> {

    Optional<NotificationAttemptEntity> findFirstByProviderMessageIdOrderByStartedAtDesc(String providerMessageId);

    Page<NotificationAttemptEntity> findByMessageIdOrderByStartedAtAscAttemptNumberAsc(String messageId, Pageable pageable);

    Page<NotificationAttemptEntity> findByCorrelationIdOrderByStartedAtAscAttemptNumberAsc(String correlationId, Pageable pageable);

    List<NotificationAttemptEntity> findByLogIdOrderByAttemptNumberAsc(String logId);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("SELECT e FROM NotificationAttemptEntity e WHERE e.messageId = :messageId AND (e.tenantId IS NULL OR e.tenantId = :tenantId) "
            + "ORDER BY e.startedAt ASC, e.attemptNumber ASC")
    Page<NotificationAttemptEntity> findByMessageIdScoped(@Param("messageId") String messageId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT e FROM NotificationAttemptEntity e WHERE e.correlationId = :correlationId AND (e.tenantId IS NULL OR e.tenantId = :tenantId) "
            + "ORDER BY e.startedAt ASC, e.attemptNumber ASC")
    Page<NotificationAttemptEntity> findByCorrelationIdScoped(@Param("correlationId") String correlationId,
            @Param("tenantId") String tenantId, Pageable pageable);

    @Transactional
    long deleteByStartedAtBefore(LocalDateTime cutoff);
}
