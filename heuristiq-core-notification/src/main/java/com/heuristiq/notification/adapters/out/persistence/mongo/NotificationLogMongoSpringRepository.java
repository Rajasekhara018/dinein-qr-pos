package com.railcab.nms.adapters.out.persistence.mongo;

import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.adapters.out.persistence.mongo.NotificationLogDocument;

interface NotificationLogMongoSpringRepository extends MongoRepository<NotificationLogDocument, String> {

    Page<NotificationLogDocument> findByEvent(String event, Pageable pageable);

    Page<NotificationLogDocument> findByCorrelationId(String correlationId, Pageable pageable);

    Page<NotificationLogDocument> findByChannel(NotificationChannel channel, Pageable pageable);

    Page<NotificationLogDocument> findBySentAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByChannelAndSuccessTrue(NotificationChannel channel);

    long countByChannelAndSuccessFalse(NotificationChannel channel);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query("{ $or: [ { 'tenantId': null }, { 'tenantId': ?0 } ] }")
    Page<NotificationLogDocument> findAllScoped(String tenantId, Pageable pageable);

    @Query("{ 'event': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    Page<NotificationLogDocument> findByEventScoped(String event, String tenantId, Pageable pageable);

    @Query("{ 'correlationId': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    Page<NotificationLogDocument> findByCorrelationIdScoped(String correlationId, String tenantId, Pageable pageable);

    @Query("{ 'channel': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    Page<NotificationLogDocument> findByChannelScoped(NotificationChannel channel, String tenantId, Pageable pageable);

    @Query("{ 'sentAt': { $gt: ?0, $lt: ?1 }, $or: [ { 'tenantId': null }, { 'tenantId': ?2 } ] }")
    Page<NotificationLogDocument> findBySentAtBetweenScoped(LocalDateTime from, LocalDateTime to, String tenantId, Pageable pageable);

    @Query(value = "{ 'channel': ?0, 'success': ?1, $or: [ { 'tenantId': null }, { 'tenantId': ?2 } ] }", count = true)
    long countByChannelAndSuccessScoped(NotificationChannel channel, boolean success, String tenantId);
}
