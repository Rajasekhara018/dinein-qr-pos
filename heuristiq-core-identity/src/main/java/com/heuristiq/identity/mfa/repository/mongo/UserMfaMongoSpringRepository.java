package com.heuristiq.uam.mfa.repository.mongo;

import com.heuristiq.uam.mfa.persistence.mongo.UserMfaDocument;
import com.heuristiq.uam.enums.UamEnums.MfaType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UserMfaMongoSpringRepository extends MongoRepository<UserMfaDocument, String> {
    List<UserMfaDocument> findByUserId(String userId);

    Optional<UserMfaDocument> findByUserIdAndMfaType(String userId, MfaType mfaType);
}
