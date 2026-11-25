package com.heuristiq.identity.feature.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;


@Component(ObservabilityConstants.PERSISTENT_STATE_BEAN)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class UamPersistentStateHealthIndicator implements HealthIndicator {
    private final MongoTemplate mongoTemplate;

    public UamPersistentStateHealthIndicator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        ObservabilityConstants.PERSISTENT_STATE_STORES.forEach((name, collection) ->
                builder.withDetail(name, mongoTemplate.collectionExists(collection) ? ObservabilityConstants.STORE_AVAILABLE : ObservabilityConstants.STORE_PENDING));
        return builder.build();
    }
}
