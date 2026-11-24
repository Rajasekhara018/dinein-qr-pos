package com.railcab.nms.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning of the internal readiness endpoint ({@code nms.readiness.*}).
 *
 * @param timeBudget the longest the whole evaluation may take; a check still running then is UNKNOWN (default 2 s)
 */
@ConfigurationProperties(prefix = "nms.readiness")
public record ReadinessProperties(@DefaultValue("2s") Duration timeBudget) {

    /** Fallback when the budget is missing or not positive. */
    public static final Duration DEFAULT_TIME_BUDGET = Duration.ofSeconds(2);

    public ReadinessProperties {
        if (timeBudget == null || timeBudget.isZero() || timeBudget.isNegative()) {
            timeBudget = DEFAULT_TIME_BUDGET;
        }
    }
}
