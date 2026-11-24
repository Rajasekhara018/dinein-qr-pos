package com.railcab.nms.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import static com.railcab.nms.infrastructure.config.NmsConfigConstants.*;

/**
 * Resilience for NMS -> audit service pushes. The drain runs on a scheduler thread, so a short
 * timeout and a breaker that opens quickly keep a down audit service from tying it up; the outbox
 * (not this caller) is what guarantees the events are not lost.
 */
@Configuration
public class AuditPublishResilienceConfig {

    @Bean
    public ResilientCaller auditResilientCaller(NmsProperties props) {
        NmsProperties.Audit.Publish cfg = props.getAudit().getPublish();
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(cfg.getCallTimeoutMs()))
                .withMaxAttempts(cfg.getCallMaxAttempts())
                .withMaxConcurrentCalls(AUDIT_MAX_CONCURRENT_CALLS)
                .withBreaker(AUDIT_BREAKER_WINDOW, AUDIT_BREAKER_MIN_CALLS, AUDIT_BREAKER_FAILURE_PERCENT, AUDIT_BREAKER_OPEN);
        return new ResilientCaller(AUDIT_CALLER_NAME, profile);
    }
}
