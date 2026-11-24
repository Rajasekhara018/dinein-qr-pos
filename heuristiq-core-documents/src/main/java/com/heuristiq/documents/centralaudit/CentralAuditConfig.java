package com.railcab.dms.centralaudit;

import com.railcab.dms.common.DmsCommonConstants;
import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Wiring for the DMS -> audit service push: properties, the resilient caller and the WebClient. A short timeout and a
 * breaker that opens quickly keep a down audit service from tying up the scheduler thread; the outbox (not this
 * caller) is what guarantees events are not lost. Wiring only; no logic.
 */
@Configuration
@EnableConfigurationProperties(AuditPublishProperties.class)
public class CentralAuditConfig {

    @Bean(CentralAuditConstants.AUDIT_RESILIENT_CALLER_BEAN)
    public ResilientCaller auditResilientCaller(AuditPublishProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(properties.callTimeoutMs()))
                .withMaxAttempts(properties.callMaxAttempts())
                .withMaxConcurrentCalls(CentralAuditConstants.MAX_CONCURRENT_CALLS)
                .withBreaker(CentralAuditConstants.BREAKER_WINDOW, CentralAuditConstants.BREAKER_MIN_CALLS,
                        CentralAuditConstants.BREAKER_FAILURE_PERCENT, CentralAuditConstants.BREAKER_OPEN);
        return new ResilientCaller(CentralAuditConstants.AUDIT_DOWNSTREAM, profile);
    }

    /**
     * WebClient toward the audit service: direct when {@code base-url} is set, otherwise the Eureka name in
     * {@code discovery-url} through the load balancer. Built from a clone so the load-balancing filter never leaks
     * onto the other WebClients of this service.
     */
    @Bean(CentralAuditConstants.AUDIT_WEB_CLIENT_BEAN)
    public WebClient auditWebClient(WebClient.Builder builder, AuditPublishProperties properties,
                                    ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancer) {
        boolean explicit = StringUtils.hasText(properties.baseUrl());
        String base = (explicit ? properties.baseUrl() : properties.discoveryUrl()).trim()
                .replaceAll(DmsCommonConstants.TRAILING_SLASHES_REGEX, "");
        WebClient.Builder clone = builder.clone().baseUrl(base).filter(CorrelationIdPropagation.filter());
        if (!explicit) {
            ReactorLoadBalancerExchangeFilterFunction lb = loadBalancer.getIfAvailable();
            if (lb != null) {
                clone.filter(lb);
            }
        }
        return clone.build();
    }
}
