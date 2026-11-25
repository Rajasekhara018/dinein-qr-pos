package com.heuristiq.identity.centralaudit;

import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Wiring for the UAM to Rider_AuditService push: properties, a WebClient carrying the correlation id, a resilient
 * caller with its own timeout, bulkhead and breaker, the sink, the outbox for the active persistence mode and the publisher. Wiring only; no logic.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CentralAuditProperties.class)
public class CentralAuditConfig {

    @Bean(CentralAuditConstants.AUDIT_WEB_CLIENT_BEAN)
    WebClient centralAuditWebClient(WebClient.Builder builder, CentralAuditProperties properties) {
        return builder.clone().baseUrl(properties.baseUrl()).filter(CorrelationIdPropagation.filter()).build();
    }

    @Bean(CentralAuditConstants.AUDIT_RESILIENT_CALLER_BEAN)
    ResilientCaller centralAuditResilientCaller(CentralAuditProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(properties.callTimeoutMs()))
                .withMaxAttempts(properties.maxAttempts())
                .withMaxConcurrentCalls(properties.maxConcurrentCalls());
        return new ResilientCaller(CentralAuditConstants.AUDIT_DOWNSTREAM, profile);
    }

    @Bean
    CentralAuditSink centralAuditSink(@Qualifier(CentralAuditConstants.AUDIT_WEB_CLIENT_BEAN) WebClient client,
                                      @Qualifier(CentralAuditConstants.AUDIT_RESILIENT_CALLER_BEAN) ResilientCaller caller,
                                      CentralAuditProperties properties) {
        return new HttpCentralAuditSink(client, caller, properties.serviceToken());
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    CentralAuditOutbox centralAuditJdbcOutbox(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcCentralAuditOutbox(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    CentralAuditOutbox centralAuditMongoOutbox(MongoTemplate mongo) {
        return new MongoCentralAuditOutbox(mongo);
    }

    @Bean
    CentralAuditPublisher centralAuditPublisher(CentralAuditProperties properties, CentralAuditOutbox outbox,
                                                CentralAuditSink sink, ObjectProvider<MeterRegistry> metrics) {
        return new CentralAuditPublisher(properties, outbox, sink, Clock.systemUTC(), metrics.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    CentralAuditLogService centralAuditLogService(CentralAuditPublisher publisher) {
        return new CentralAuditLogService(publisher, Clock.systemUTC());
    }
}
