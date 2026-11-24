package com.heuristiq.payment.config;

import com.heuristiq.resilient.ResilienceProfile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code rider.wallet.*}: where Rider_WalletService is, which wallet app the top-ups belong to, the limits of the
 * Payment -> Wallet call, and how the retry job re-tries a paid top-up whose credit has not gone through.
 */
@Component
@ConfigurationProperties(prefix = "rider.wallet")
@Getter
@Setter
public class WalletServiceProperties {

    /** Base URL of the wallet's internal API (service token only), without a trailing slash. */
    private String baseUrl = "http://localhost:9121/api/v1/wallet/internal";
    /** The wallet app code the top-up credits are booked to. */
    private String appCode = "BESTRIDE_RIDER_APP";

    private long callTimeoutMs = 5000;
    private int maxAttempts = 3;
    private int maxConcurrentCalls = 25;
    private int breakerWindowSize = 20;
    private int breakerMinimumCalls = 10;
    private float breakerFailureRatePercent = 50f;
    private long breakerOpenSeconds = 15;

    /** Most paid top-ups the retry job credits in one run (bounds the work and the calls per run). */
    private int creditRetryBatchSize = 50;
    /** Delay before the second credit attempt; doubles after each failed attempt. */
    private long creditRetryInitialBackoffSeconds = 30;
    /** Ceiling of the backoff between two attempts for one top-up. */
    private long creditRetryMaxBackoffSeconds = 3600;
    /** From this many failed attempts a top-up is logged at ERROR on every attempt so that monitoring raises it. */
    private int creditAlertAfterAttempts = 10;

    public ResilienceProfile toProfile() {
        return ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(callTimeoutMs))
                .withMaxAttempts(maxAttempts)
                .withMaxConcurrentCalls(maxConcurrentCalls)
                .withBreaker(breakerWindowSize, breakerMinimumCalls, breakerFailureRatePercent,
                        Duration.ofSeconds(breakerOpenSeconds));
    }
}
