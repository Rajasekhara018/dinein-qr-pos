package com.railcab.nms.infrastructure.config;

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
 * itself comes from the property default or is fully replaced by the MONGO_URI env var.
 * Mirrors PaymentAPI/UAM's MongoPoolConfig -- without this, the driver runs on its (much
 * smaller) built-in defaults despite every other connection pool in this service being
 * explicitly tuned (see NmsBeans.webClient).
 */
@Configuration
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoPoolConfig {

    @Value("${nms.mongo.pool.min-size:10}")
    private int minSize;

    @Value("${nms.mongo.pool.max-size:100}")
    private int maxSize;

    @Value("${nms.mongo.pool.wait-queue-timeout-ms:5000}")
    private int waitQueueTimeoutMs;

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolSettingsCustomizer() {
        return (MongoClientSettings.Builder builder) -> builder.applyToConnectionPoolSettings(pool -> pool
                .minSize(minSize)
                .maxSize(maxSize)
                .maxWaitTime(waitQueueTimeoutMs, TimeUnit.MILLISECONDS));
    }
}
