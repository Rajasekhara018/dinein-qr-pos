package com.heuristiq.uam.usercredentials.repository.mongo;

import com.heuristiq.uam.usercredentials.persistance.mongo.UserCredentialDocument;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UserCredentialMongoSpringRepository extends MongoRepository<UserCredentialDocument, String> {
    List<UserCredentialDocument> findByUserId(String userId);

    Optional<UserCredentialDocument> findByUserIdAndProvider(String userId, AuthProvider provider);

    Optional<UserCredentialDocument> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
