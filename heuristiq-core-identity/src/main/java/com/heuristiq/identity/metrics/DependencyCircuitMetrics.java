package com.heuristiq.uam.metrics;

import com.heuristiq.resilient.ResilientCaller;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Publishes the circuit-breaker state of every {@link ResilientCaller} bean (NMS, DMS). Those breakers are
 * created standalone, not through the Spring-managed Resilience4j registry, so the starter's own
 * {@code resilience4j_circuitbreaker_*} metrics do not cover them (it covers keycloak, oidc-provider and
 * google-identity). The gauge is read only when Prometheus scrapes.
 */
@Component
public class DependencyCircuitMetrics implements MeterBinder {

    private final ObjectProvider<ResilientCaller> callers;

    public DependencyCircuitMetrics(ObjectProvider<ResilientCaller> callers) {
        this.callers = callers;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (ResilientCaller caller : callers.orderedStream().toList()) {
            Gauge.builder(UamMetricNames.DEPENDENCY_CIRCUIT_STATE, caller.circuitBreaker(), DependencyCircuitMetrics::stateValue)
                    .description("Outbound circuit breaker state: 0 closed, 1 half open, 2 open")
                    .tag(UamMetricNames.TAG_NAME, caller.name())
                    .register(registry);
        }
    }

    /** Maps a breaker state to the gauge value; forced-open counts as open, every other state as closed. */
    static double stateValue(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case OPEN, FORCED_OPEN -> UamMetricNames.CIRCUIT_OPEN;
            case HALF_OPEN -> UamMetricNames.CIRCUIT_HALF_OPEN;
            default -> UamMetricNames.CIRCUIT_CLOSED;
        };
    }
}
