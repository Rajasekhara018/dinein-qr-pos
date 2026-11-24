package com.railcab.dms.config;

import com.railcab.dms.common.DmsCommonConstants;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Explicit connection-pool sizing for the Mongo driver, applied via a settings customizer
 * (not query params on spring.data.mongodb.uri) so it takes effect regardless of whether the
 * URI comes from the property default or is fully replaced by the DMS_MONGO_URI env var.
 * Mirrors PaymentAPI/UAM/NMS's MongoPoolConfig -- without this, the driver runs on its (much
 * smaller) built-in defaults despite the Postgres side of this same dual-backend service
 * (PostgresPersistenceConfig) being explicitly pool-tuned.
 */
@Configuration
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoPoolConfig {

    @Value("${railcab.dms.mongo.pool.min-size:10}")
    private int minSize;

    @Value("${railcab.dms.mongo.pool.max-size:100}")
    private int maxSize;

    @Value("${railcab.dms.mongo.pool.wait-queue-timeout-ms:5000}")
    private int waitQueueTimeoutMs;

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolSettingsCustomizer() {
        return (MongoClientSettings.Builder builder) -> builder.applyToConnectionPoolSettings(pool -> pool
                .minSize(minSize)
                .maxSize(maxSize)
                .maxWaitTime(waitQueueTimeoutMs, TimeUnit.MILLISECONDS));
    }
}
