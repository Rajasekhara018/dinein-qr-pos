package com.heuristiq.paymentservice.gatewayaudit.publish;

import com.heuristiq.resilient.ResilienceProfile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code rider.resilience.audit.*}: limits for Payment -> Audit calls (same shape as the Ride ones). */
@Component
@ConfigurationProperties(prefix = "rider.resilience.audit")
@Getter
@Setter
public class AuditResilienceProperties {
    private long callTimeoutMs = 5000;
    private int maxAttempts = 3;
    private int maxConcurrentCalls = 5;
    private int breakerWindowSize = 20;
    private int breakerMinimumCalls = 10;
    private float breakerFailureRatePercent = 50f;
    private long breakerOpenSeconds = 30;

    public ResilienceProfile toProfile() {
        return ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls)
                .withBreaker(breakerWindowSize, breakerMinimumCalls, breakerFailureRatePercent,
                        Duration.ofSeconds(breakerOpenSeconds));
    }
}
