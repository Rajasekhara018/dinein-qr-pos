package com.railcab.nms.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

/**
 * Resilience for NMS -> UAM audience lookups ({@code nms.integration.uam.resilience.*}). Paging UAM
 * is a sequence of GETs on a scheduler thread (not a user request), so a few retries are affordable;
 * the breaker stops a sweep from hammering a UAM that is down.
 */
@Configuration
public class UamResilienceConfig {

    @Bean
    public ResilientCaller uamResilientCaller(
            @Value("${nms.integration.uam.resilience.call-timeout-ms:10000}") long callTimeoutMs,
            @Value("${nms.integration.uam.resilience.max-attempts:3}") int maxAttempts,
            @Value("${nms.integration.uam.resilience.max-concurrent-calls:5}") int maxConcurrentCalls,
            @Value("${nms.integration.uam.resilience.breaker-window-size:10}") int windowSize,
            @Value("${nms.integration.uam.resilience.breaker-minimum-calls:5}") int minimumCalls,
            @Value("${nms.integration.uam.resilience.breaker-failure-rate-percent:50}") float failureRatePercent,
            @Value("${nms.integration.uam.resilience.breaker-open-ms:30000}") long breakerOpenMs) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls)
                .withBreaker(windowSize, minimumCalls, failureRatePercent, Duration.ofMillis(breakerOpenMs));
        return new ResilientCaller(NmsConfigConstants.UAM_CALLER_NAME, profile);
    }
}
