package com.heuristiq.identity.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

/**
 * Wiring for the franchise claim lookup ({@code railcab.uam.franchise.service.*}): the franchise service lives outside UAM,
 * so the call has its own timeout, retry (the lookup is a read), bulkhead and circuit breaker.
 */
@Configuration
public class FranchiseClaimsConfig {

    @Bean
    FranchiseClaims franchiseClaims(WebClient.Builder builder,
            @Value("${railcab.uam.franchise.service.enabled:true}") boolean enabled,
            @Value("${railcab.uam.franchise.service.base-url:http://localhost:9124}") String baseUrl,
            @Value("${railcab.uam.franchise.service.call-timeout-ms:3000}") long callTimeoutMs,
            @Value("${railcab.uam.franchise.service.max-attempts:2}") int maxAttempts,
            @Value("${railcab.uam.franchise.service.max-concurrent-calls:20}") int maxConcurrentCalls,
            @Value("${rider.internal.service-token:}") String serviceToken) {
        WebClient client = builder.baseUrl(baseUrl).filter(CorrelationIdPropagation.filter()).build();
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls);
        return new RemoteFranchiseClaims(client, new ResilientCaller(SecurityConstants.FRANCHISE_SERVICE_NAME, profile), serviceToken, enabled);
    }
}
