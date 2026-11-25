package com.heuristiq.documents.search.projection.config;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.search.projection.ProjectionConstants;
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

/** Wiring of the resilient client toward Rider_SearchAPI's projection ingress. Wiring only; no logic. */
@Configuration
@EnableConfigurationProperties(SearchProjectionProperties.class)
public class SearchProjectionConfig {

    /** Timeout, bulkhead and breaker; the ingress steps are idempotent, so a transient failure is retried. */
    @Bean(ProjectionConstants.RESILIENT_CALLER_BEAN)
    public ResilientCaller searchProjectionResilientCaller(SearchProjectionProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(Math.max(properties.timeoutMs(), ProjectionConstants.MIN_TIMEOUT_MS)))
                .withMaxConcurrentCalls(ProjectionConstants.MAX_CONCURRENT_CALLS)
                .withBreaker(ProjectionConstants.BREAKER_WINDOW, ProjectionConstants.BREAKER_MIN_CALLS,
                        ProjectionConstants.BREAKER_FAILURE_PERCENT, ProjectionConstants.BREAKER_OPEN);
        return new ResilientCaller(ProjectionConstants.DOWNSTREAM, profile);
    }

    /**
     * WebClient toward the Search API: direct when {@code base-url} is set, otherwise the Eureka name through the load
     * balancer. Built from a clone so the load-balancing filter never leaks onto the other WebClients of this service.
     */
    @Bean(ProjectionConstants.WEB_CLIENT_BEAN)
    public WebClient searchProjectionWebClient(WebClient.Builder builder, SearchProjectionProperties properties,
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
