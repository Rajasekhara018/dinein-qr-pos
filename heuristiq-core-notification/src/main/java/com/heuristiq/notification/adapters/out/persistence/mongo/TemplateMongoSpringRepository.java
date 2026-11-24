package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.adapters.out.persistence.mongo.NotificationTemplateDocument;

interface TemplateMongoSpringRepository extends MongoRepository<NotificationTemplateDocument, String> {

    List<NotificationTemplateDocument> findByEventAndActiveTrue(String event);

    Optional<NotificationTemplateDocument> findByEventAndChannelAndTenantIdIsNullAndActiveTrue(String event, NotificationChannel channel);

    Optional<NotificationTemplateDocument> findByEventAndChannelAndTenantIdAndActiveTrue(String event, NotificationChannel channel, String tenantId);

    // tenantIds may contain null, which matches the shared templates (no organization stored).
    Page<NotificationTemplateDocument> findByActiveTrueAndTenantIdIn(java.util.Collection<String> tenantIds, Pageable pageable);

    List<NotificationTemplateDocument> findByActiveTrue();

    List<NotificationTemplateDocument> findByTenantIdIsNullAndActiveTrue();

    List<NotificationTemplateDocument> findByTenantId(String tenantId);

    Page<NotificationTemplateDocument> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    Page<NotificationTemplateDocument> findByActiveTrue(Pageable pageable);

    boolean existsByEventAndChannelAndTenantIdIsNull(String event, NotificationChannel channel);

    boolean existsByEventAndChannelAndTenantId(String event, NotificationChannel channel, String tenantId);
}
