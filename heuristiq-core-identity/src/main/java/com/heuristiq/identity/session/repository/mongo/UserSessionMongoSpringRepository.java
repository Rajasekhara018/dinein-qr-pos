package com.heuristiq.uam.session.repository.mongo;

import com.heuristiq.uam.session.persistence.mongo.UserSessionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UserSessionMongoSpringRepository extends MongoRepository<UserSessionDocument, String> {
    List<UserSessionDocument> findByUserIdAndActiveTrue(String userId);

    Optional<UserSessionDocument> findBySessionId(String sessionId);
}
