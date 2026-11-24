package com.railcab.nms.adapters.out.persistence.mongo;

import org.springframework.data.mongodb.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.railcab.nms.adapters.out.persistence.mongo.DeviceTokenDocument;

interface DeviceTokenMongoSpringRepository extends MongoRepository<DeviceTokenDocument, String> {

    boolean existsByUserIdAndToken(String userId, String token);

    void deleteByUserIdAndToken(String userId, String token);

    void deleteByToken(String token);

    List<DeviceTokenDocument> findByUserId(String userId);

    /** Drops the same token registered under any OTHER user (see IDeviceTokenRepository#register). */
    void deleteByTokenAndUserIdNot(String token, String userId);

    Optional<DeviceTokenDocument> findFirstByUserIdAndToken(String userId, String token);

    List<DeviceTokenDocument> findByUserIdIn(Collection<String> userIds);

    @Query("{ 'userId': { $in: ?0 }, $or: [ { 'tenantId': null }, { 'tenantId': ?1 } ] }")
    List<DeviceTokenDocument> findByUserIdInScoped(Collection<String> userIds, String tenantId);
}
