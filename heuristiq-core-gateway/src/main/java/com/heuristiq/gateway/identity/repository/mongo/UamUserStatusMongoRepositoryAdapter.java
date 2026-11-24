package com.heuristiq.gateway.identity.repository.mongo;

import com.heuristiq.gateway.identity.IdentityConstants;
import com.heuristiq.gateway.identity.UamUserStatusRepository;
import com.heuristiq.gateway.identity.persistence.mongo.GatewayUamUserDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "railcab.gateway.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UamUserStatusMongoRepositoryAdapter implements UamUserStatusRepository {

    private final MongoTemplate uamMongoTemplate;

    UamUserStatusMongoRepositoryAdapter(@Qualifier("uamMongoTemplate") MongoTemplate uamMongoTemplate) {
        this.uamMongoTemplate = uamMongoTemplate;
    }

    @Override
    public boolean isActive(String userId) {
        Query query = new Query(
                Criteria.where("_id").is(userId).and("status").is(IdentityConstants.ACTIVE_STATUS));
        return uamMongoTemplate.exists(query, GatewayUamUserDocument.class);
    }
}
