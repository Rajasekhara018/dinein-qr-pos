package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationTemplateEntity;

interface TemplateJpaSpringRepository extends JpaRepository<NotificationTemplateEntity, String> {

    List<NotificationTemplateEntity> findByEventAndActiveTrue(String event);

    Optional<NotificationTemplateEntity> findByEventAndChannelAndTenantIdIsNullAndActiveTrue(String event, NotificationChannel channel);

    Optional<NotificationTemplateEntity> findByEventAndChannelAndTenantIdAndActiveTrue(String event, NotificationChannel channel, String tenantId);

    // The caller's organization plus the shared templates.
    @Query("SELECT t FROM NotificationTemplateEntity t WHERE t.active = true AND (t.tenantId IS NULL OR t.tenantId = :tenantId)")
    Page<NotificationTemplateEntity> findActiveScoped(@Param("tenantId") String tenantId, Pageable pageable);

    List<NotificationTemplateEntity> findByActiveTrue();

    List<NotificationTemplateEntity> findByTenantIdIsNullAndActiveTrue();

    List<NotificationTemplateEntity> findByTenantId(String tenantId);

    Page<NotificationTemplateEntity> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    Page<NotificationTemplateEntity> findByActiveTrue(Pageable pageable);

    boolean existsByEventAndChannelAndTenantIdIsNull(String event, NotificationChannel channel);

    boolean existsByEventAndChannelAndTenantId(String event, NotificationChannel channel, String tenantId);
}
