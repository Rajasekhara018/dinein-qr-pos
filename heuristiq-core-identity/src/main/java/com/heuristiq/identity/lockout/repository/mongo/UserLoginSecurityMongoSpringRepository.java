package com.heuristiq.uam.lockout.repository.mongo;

import com.heuristiq.uam.lockout.persistence.mongo.UserLoginSecurityDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface UserLoginSecurityMongoSpringRepository extends MongoRepository<UserLoginSecurityDocument, String> {
    Optional<UserLoginSecurityDocument> findByUserId(String userId);
}
