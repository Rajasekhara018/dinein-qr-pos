package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the access-audit publisher (only when {@code gateway.audit.enabled} is true, the default). The audit
 * service is found at {@code gateway.audit.base-url}, else through Eureka by {@code gateway.audit.service-id},
 * else at localhost:9106 when discovery is off. Calls are guarded by the {@code audit-publisher} circuit breaker.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "gateway.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditConfig {

    @Bean
    public AuditEventQueue auditEventQueue(AuditProperties properties) {
        return new AuditEventQueue(properties.getQueueCapacity(), properties.getMaxQueueBytes());
    }

    @Bean
    public AuditEventSink auditEventSink(AuditProperties properties,
                                         ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancer,
                                         ObjectProvider<CircuitBreakerRegistry> registry,
                                         @Value("${railcab.gateway.discovery.enabled:true}") boolean discoveryEnabled) {
        if (!StringUtils.hasText(properties.getServiceToken())) {
            log.warn("gateway.audit.service-token is empty: the audit service will refuse the gateway's events. "
                    + "Set RIDER_INTERNAL_SERVICE_TOKEN.");
        }
        WebClient.Builder builder = WebClient.builder();
        if (StringUtils.hasText(properties.getBaseUrl())) {
            builder.baseUrl(properties.getBaseUrl());
        } else if (discoveryEnabled && loadBalancer.getIfAvailable() != null) {
            builder.baseUrl("http://" + properties.getServiceId()).filter(loadBalancer.getIfAvailable());
        } else {
            builder.baseUrl(LOCAL_URL);
        }
        CircuitBreakerRegistry cbRegistry = registry.getIfAvailable(CircuitBreakerRegistry::ofDefaults);
        CircuitBreaker breaker = cbRegistry.circuitBreaker(CIRCUIT_BREAKER);
        return new AuditServiceSink(builder.build(), properties.getServiceToken(),
                Duration.ofMillis(properties.getTimeoutMs()), breaker);
    }

    /** UTC clock used by the audit classes (tests replace it); skipped when the application already has one. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock auditClock() {
        return Clock.systemUTC();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public AuditBatcher auditBatcher(AuditEventQueue queue, AuditEventSink sink, AuditEventRedactor redactor,
                                     AuditProperties properties, Clock clock) {
        return new AuditBatcher(queue, sink, redactor, properties, clock::millis);
    }

    @Bean
    public AuditEventPublisher auditEventPublisher(AuditProperties properties, AuditPathRules rules,
                                                   AuditEventFactory factory, AuditEventQueue queue, AuditBatcher batcher,
                                                   Clock clock) {
        return new AuditEventPublisher(properties, rules, factory, queue, batcher, clock);
    }
}
