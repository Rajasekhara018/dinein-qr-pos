package com.heuristiq.identity.readiness.config;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import com.heuristiq.identity.readiness.adapter.out.ReadinessAuthMode;

/**
 * Settings of the readiness combiner ({@code railcab.uam.readiness.*}).
 *
 * @param budget              overall wait for the services of one request
 * @param cacheTtl            how long a result is reused per organization and application
 * @param cacheMaxSize        most results kept in memory
 * @param maxConcurrentCalls  threads of the bounded executor running the service calls
 * @param queueCapacity       calls that may wait for a thread; beyond it a call is refused (UNKNOWN, reason BUSY)
 * @param callTimeout         per-attempt timeout of one service call
 * @param maxAttempts         attempts per call (1 = no retry, 2 = one retry; never more)
 * @param breakerWindowSize   calls in each service's circuit-breaker window
 * @param breakerMinimumCalls calls before the breaker may open
 * @param breakerFailureRate  failure percentage that opens the breaker
 * @param breakerOpen         how long an open breaker refuses calls
 * @param serviceJwtTtl       lifetime of the SERVICE bearer token minted per call
 * @param services            per service ({@code wallet}, {@code reports}, {@code onboarding}, {@code dms}, {@code nms}): base URL,
 *                            readiness path and credential; a missing value falls back to the built-in default
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.readiness")
public record ReadinessProperties(
        @DefaultValue("4s") @NotNull Duration budget,
        @DefaultValue("30s") @NotNull Duration cacheTtl,
        @DefaultValue("500") @Min(1) long cacheMaxSize,
        @DefaultValue("32") @Min(1) int maxConcurrentCalls,
        @DefaultValue("256") @Min(1) int queueCapacity,
        @DefaultValue("2s") @NotNull Duration callTimeout,
        @DefaultValue("2") @Min(1) @Max(2) int maxAttempts,
        @DefaultValue("10") @Min(1) int breakerWindowSize,
        @DefaultValue("5") @Min(1) int breakerMinimumCalls,
        @DefaultValue("50") @Min(1) @Max(100) float breakerFailureRate,
        @DefaultValue("30s") @NotNull Duration breakerOpen,
        @DefaultValue("5m") @NotNull Duration serviceJwtTtl,
        Map<String, Service> services) {

    public ReadinessProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    /**
     * One service's readiness endpoint.
     *
     * @param baseUrl service root (scheme, host, port)
     * @param path    readiness path
     * @param auth    which credential it expects
     */
    public record Service(String baseUrl, String path, ReadinessAuthMode auth) {
    }
}
