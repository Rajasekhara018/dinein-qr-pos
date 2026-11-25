package com.heuristiq.identity.mfa.repository.mongo;

import com.heuristiq.identity.mfa.persistence.mongo.UserMfaDocument;
import com.heuristiq.identity.enums.UamEnums.MfaType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UserMfaMongoSpringRepository extends MongoRepository<UserMfaDocument, String> {
    List<UserMfaDocument> findByUserId(String userId);

    Optional<UserMfaDocument> findByUserIdAndMfaType(String userId, MfaType mfaType);
}
