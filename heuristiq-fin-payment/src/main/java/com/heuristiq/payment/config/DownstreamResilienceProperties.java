package com.heuristiq.paymentservice.config;

import com.heuristiq.resilient.ResilienceProfile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code rider.resilience.ride.*}: limits for Payment -> Ride calls. Defaults match {@link ResilienceProfile#defaults()}. */
@Component
@ConfigurationProperties(prefix = "rider.resilience.ride")
@Getter
@Setter
public class DownstreamResilienceProperties {
    private long callTimeoutMs = 5000;
    private int maxAttempts = 3;
    private int maxConcurrentCalls = 25;
    private int breakerWindowSize = 20;
    private int breakerMinimumCalls = 10;
    private float breakerFailureRatePercent = 50f;
    private long breakerOpenSeconds = 15;

    public ResilienceProfile toProfile() {
        return ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls)
                .withBreaker(breakerWindowSize, breakerMinimumCalls, breakerFailureRatePercent,
                        Duration.ofSeconds(breakerOpenSeconds));
    }
}
