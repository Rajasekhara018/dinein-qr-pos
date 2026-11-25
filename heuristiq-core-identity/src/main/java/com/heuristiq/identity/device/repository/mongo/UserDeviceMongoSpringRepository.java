package com.heuristiq.identity.device.repository.mongo;

import com.heuristiq.identity.device.persistence.mongo.UserDeviceDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UserDeviceMongoSpringRepository extends MongoRepository<UserDeviceDocument, String> {
    List<UserDeviceDocument> findByUserId(String userId);

    Optional<UserDeviceDocument> findByUserIdAndDeviceFingerprint(String userId, String deviceFingerprint);
}
