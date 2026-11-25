package com.heuristiq.notification.adapters.out.persistence.mongo;

import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.adapters.out.persistence.mongo.NotificationMessageDocument;

interface NotificationMessageMongoSpringRepository extends MongoRepository<NotificationMessageDocument, String> {

    Optional<NotificationMessageDocument> findByMessageId(String messageId);

    Optional<NotificationMessageDocument> findFirstByMessageIdAndStatusAndUpdatedAtBefore(
            String messageId, NotificationMessageStatus status, LocalDateTime updatedBefore);

    Page<NotificationMessageDocument> findByCorrelationId(String correlationId, Pageable pageable);

    Page<NotificationMessageDocument> findByStatus(NotificationMessageStatus status, Pageable pageable);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("{ $or: [ { 'tenantId': null }, { 'tenantId': ?0 } ] }")
    Page<NotificationMessageDocument> findAllScoped(String tenantId, Pageable pageable);

    @Query("{ 'correlationId': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    Page<NotificationMessageDocument> findByCorrelationIdScoped(String correlationId, String tenantId, Pageable pageable);

    @Query("{ 'status': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    Page<NotificationMessageDocument> findByStatusScoped(NotificationMessageStatus status, String tenantId, Pageable pageable);
}
