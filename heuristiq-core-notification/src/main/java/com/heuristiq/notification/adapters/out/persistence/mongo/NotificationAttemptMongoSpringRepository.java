package com.railcab.nms.adapters.out.persistence.mongo;

import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface NotificationAttemptMongoSpringRepository extends MongoRepository<NotificationAttemptDocument, String> {

    Optional<NotificationAttemptDocument> findFirstByProviderMessageIdOrderByStartedAtDesc(String providerMessageId);

    Page<NotificationAttemptDocument> findByMessageIdOrderByStartedAtAscAttemptNumberAsc(String messageId, Pageable pageable);

    Page<NotificationAttemptDocument> findByCorrelationIdOrderByStartedAtAscAttemptNumberAsc(String correlationId, Pageable pageable);

    List<NotificationAttemptDocument> findByLogIdOrderByAttemptNumberAsc(String logId);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query(value = "{ 'messageId': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }", sort = "{ 'startedAt': 1, 'attemptNumber': 1 }")
    Page<NotificationAttemptDocument> findByMessageIdScoped(String messageId, String tenantId, Pageable pageable);

    @Query(value = "{ 'correlationId': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }", sort = "{ 'startedAt': 1, 'attemptNumber': 1 }")
    Page<NotificationAttemptDocument> findByCorrelationIdScoped(String correlationId, String tenantId, Pageable pageable);
}
