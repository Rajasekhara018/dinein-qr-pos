package com.heuristiq.identity.userpreference.repository.mongo;

import com.heuristiq.identity.userpreference.persistance.mongo.UserPreferenceDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface UserPreferenceMongoSpringRepository extends MongoRepository<UserPreferenceDocument, String> {
    Optional<UserPreferenceDocument> findByUserIdAndApplicationId(String userId, String applicationId);
}
