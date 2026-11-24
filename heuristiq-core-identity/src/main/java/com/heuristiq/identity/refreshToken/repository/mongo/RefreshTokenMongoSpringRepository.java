package com.heuristiq.uam.refreshToken.repository.mongo;

import com.heuristiq.uam.refreshToken.persistance.mongo.RefreshTokenDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface RefreshTokenMongoSpringRepository extends MongoRepository<RefreshTokenDocument, String> {
    Optional<RefreshTokenDocument> findByTokenId(String tokenId);

    Optional<RefreshTokenDocument> findByTokenHash(String tokenHash);

    List<RefreshTokenDocument> findByPrincipalIdAndRevokedFalse(String principalId);

    List<RefreshTokenDocument> findByFamilyIdAndRevokedFalse(String familyId);
}
