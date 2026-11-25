package com.heuristiq.identity.config;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience for UAM -> NMS notification calls ({@code railcab.nms.resilience.*}). The calls run on
 * the async notification executor, so a few retries with backoff are affordable; the bulkhead keeps
 * a slow NMS from tying up unbounded virtual threads and the breaker stops hammering a dead one.
 */
@Configuration
public class NmsResilienceConfig {

    @Bean
    public ResilientCaller nmsResilientCaller(
            @Value("${railcab.nms.resilience.call-timeout-ms:5000}") long callTimeoutMs,
            @Value("${railcab.nms.resilience.max-attempts:3}") int maxAttempts,
            @Value("${railcab.nms.resilience.max-concurrent-calls:10}") int maxConcurrentCalls,
            @Value("${railcab.nms.resilience.breaker-window-size:20}") int windowSize,
            @Value("${railcab.nms.resilience.breaker-minimum-calls:10}") int minimumCalls,
            @Value("${railcab.nms.resilience.breaker-failure-rate-percent:50}") float failureRatePercent,
            @Value("${railcab.nms.resilience.breaker-open-ms:30000}") long breakerOpenMs) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls)
                .withBreaker(windowSize, minimumCalls, failureRatePercent, Duration.ofMillis(breakerOpenMs));
        return new ResilientCaller("nms-service", profile);
    }
}
