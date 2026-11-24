package com.heuristiq.uam.config;

import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Explicit connection-pool sizing for the Mongo driver, applied via a settings customizer
 * (not query params on spring.mongodb.uri) so it takes effect regardless of whether the URI
 * itself comes from the property default or is fully replaced by the UAM_MONGODB_URI env var.
 * Mirrors PaymentAPI's MongoPoolConfig -- without this, the driver runs on its (much smaller)
 * built-in defaults despite every other connection pool in this service being explicitly tuned.
 */
@Configuration
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoPoolConfig {

    @Value("${railcab.uam.mongo.pool.min-size:10}")
    private int minSize;

    @Value("${railcab.uam.mongo.pool.max-size:100}")
    private int maxSize;

    @Value("${railcab.uam.mongo.pool.wait-queue-timeout-ms:5000}")
    private int waitQueueTimeoutMs;

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolSettingsCustomizer() {
        return (MongoClientSettings.Builder builder) -> builder.applyToConnectionPoolSettings(pool -> pool
                .minSize(minSize)
                .maxSize(maxSize)
                .maxWaitTime(waitQueueTimeoutMs, TimeUnit.MILLISECONDS));
    }
}
