package com.railcab.dms.expiry.config;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.expiry.adapter.out.nms.ExpiryNmsConstants;
import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

/** Wiring of document expiry (D12): its properties, and the resilient caller and WebClient toward NMS. Wiring only. */
@Configuration
@EnableConfigurationProperties(DocumentExpiryProperties.class)
public class DocumentExpiryConfig {

    @Bean(ExpiryNmsConstants.NMS_RESILIENT_CALLER_BEAN)
    public ResilientCaller expiryNmsResilientCaller(DocumentExpiryProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(properties.nms().callTimeoutMs()))
                .withMaxAttempts(properties.nms().maxAttempts())
                .withMaxConcurrentCalls(ExpiryNmsConstants.MAX_CONCURRENT_CALLS)
                .withBreaker(ExpiryNmsConstants.BREAKER_WINDOW, ExpiryNmsConstants.BREAKER_MIN_CALLS,
                        ExpiryNmsConstants.BREAKER_FAILURE_PERCENT, ExpiryNmsConstants.BREAKER_OPEN);
        return new ResilientCaller(ExpiryNmsConstants.NMS_DOWNSTREAM, profile);
    }

    /**
     * WebClient toward NMS: through the load balancer for an Eureka name, direct for an address with a port or an IP. Built from a
     * clone so its filters never leak onto the other WebClients of this service.
     */
    @Bean(ExpiryNmsConstants.NMS_WEB_CLIENT_BEAN)
    public WebClient expiryNmsWebClient(WebClient.Builder builder, DocumentExpiryProperties properties,
                                        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancer) {
        String base = properties.nms().baseUrl().trim().replaceAll(DmsCommonConstants.TRAILING_SLASHES_REGEX, "");
        WebClient.Builder clone = builder.clone().baseUrl(base).filter(CorrelationIdPropagation.filter());
        ReactorLoadBalancerExchangeFilterFunction lb = loadBalancer.getIfAvailable();
        if (lb != null && !base.matches(ExpiryNmsConstants.DIRECT_ADDRESS_REGEX)) {
            clone.filter(lb);
        }
        return clone.build();
    }
}
