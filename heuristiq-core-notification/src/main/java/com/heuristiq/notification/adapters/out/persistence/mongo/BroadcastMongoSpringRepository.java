package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.heuristiq.notification.domain.model.BroadcastStatus;
import com.heuristiq.notification.adapters.out.persistence.mongo.BroadcastDocument;

interface BroadcastMongoSpringRepository extends MongoRepository<BroadcastDocument, String> {

    Page<BroadcastDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Organization-limited variant: the caller's organization plus records stored before organizations existed.
    @Query(value = "{ $or: [ { 'tenantId': null }, { 'tenantId': ?0 } ] }", sort = "{ 'createdAt': -1 }")
    Page<BroadcastDocument> findAllScoped(String tenantId, Pageable pageable);

    @Query("{ 'status': ?0, $or: [ { 'scheduledAt': null }, { 'scheduledAt': { $lte: ?1 } } ] }")
    List<BroadcastDocument> findDue(BroadcastStatus status, LocalDateTime notAfter);
}
