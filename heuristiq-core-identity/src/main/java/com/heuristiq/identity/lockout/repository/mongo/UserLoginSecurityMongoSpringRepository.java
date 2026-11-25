package com.heuristiq.identity.lockout.repository.mongo;

import com.heuristiq.identity.lockout.persistence.mongo.UserLoginSecurityDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface UserLoginSecurityMongoSpringRepository extends MongoRepository<UserLoginSecurityDocument, String> {
    Optional<UserLoginSecurityDocument> findByUserId(String userId);
}
