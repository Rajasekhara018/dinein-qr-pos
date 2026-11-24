package com.railcab.nms.adapters.out.persistence.mongo;

import org.springframework.data.mongodb.repository.Query;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.railcab.nms.adapters.out.persistence.mongo.InAppNotificationDocument;

interface InAppNotificationMongoSpringRepository extends MongoRepository<InAppNotificationDocument, String> {

    Page<InAppNotificationDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<InAppNotificationDocument> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<InAppNotificationDocument> findByUserIdAndReadFalse(String userId);

    long countByUserIdAndReadFalse(String userId);

    // Organization-limited variants: the caller's organization plus records stored before organizations existed.
    @Query(value = "{ 'userId': ?0, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }", sort = "{ 'createdAt': -1 }")
    Page<InAppNotificationDocument> findByUserIdScoped(String userId, String tenantId, Pageable pageable);

    @Query(value = "{ 'userId': ?0, 'read': false, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }", sort = "{ 'createdAt': -1 }")
    Page<InAppNotificationDocument> findUnreadByUserIdScoped(String userId, String tenantId, Pageable pageable);

    @Query("{ 'userId': ?0, 'read': false, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    List<InAppNotificationDocument> findUnreadListByUserIdScoped(String userId, String tenantId);

    @Query(value = "{ 'userId': ?0, 'read': false, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }", count = true)
    long countUnreadByUserIdScoped(String userId, String tenantId);
}
