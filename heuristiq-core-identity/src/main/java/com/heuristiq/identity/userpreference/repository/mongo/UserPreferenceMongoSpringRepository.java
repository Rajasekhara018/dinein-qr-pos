package com.heuristiq.uam.userpreference.repository.mongo;

import com.heuristiq.uam.userpreference.persistance.mongo.UserPreferenceDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface UserPreferenceMongoSpringRepository extends MongoRepository<UserPreferenceDocument, String> {
    Optional<UserPreferenceDocument> findByUserIdAndApplicationId(String userId, String applicationId);
}
