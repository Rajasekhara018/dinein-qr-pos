package com.heuristiq.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real route's retry filter together with a stand-in circuit breaker, in the order RouteConfig declares
 * them, against a fake downstream: only GETs answered 502/503/504 or failing on I/O are retried, a 500 is not, and
 * the circuit breaker's fallback answer is never retried.
 */
class RouteRetryTest {

    private final AtomicInteger downstreamCalls = new AtomicInteger();
    private final AtomicInteger fallbacks = new AtomicInteger();
    private volatile boolean breakerOpen;
    private Route route;

    /** Stand-in for the Resilience4j circuit-breaker filter: an error (or an open breaker) gives the 503 fallback. */
    private GatewayFilter fakeBreaker() {
        return (exchange, chain) -> {
            Mono<Void> call = breakerOpen ? Mono.error(new IllegalStateException("breaker open")) : chain.filter(exchange);
            return call.onErrorResume(t -> {
                fallbacks.incrementAndGet();
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return Mono.empty();
            });
        };
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void buildRoute() {
        SpringCloudCircuitBreakerFilterFactory breakerFactory = mock(SpringCloudCircuitBreakerFilterFactory.class);
        when(breakerFactory.apply(anyString(), any(java.util.function.Consumer.class))).thenAnswer(inv -> fakeBreaker());
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getBean(any(Class.class))).thenAnswer(inv -> {
            Class<?> type = inv.getArgument(0);
            return type == SpringCloudCircuitBreakerFilterFactory.class ? breakerFactory : RouteConfigTest.instantiate(type);
        });
        RouteConfig rc = RouteConfigTest.config(new GatewayProperties(), true, false);
        route = rc.railCabRoutes(new RouteLocatorBuilder(ctx)).getRoutes()
                .filter(r -> r.getId().equals("rider-ride-api")).blockFirst();
    }

    /** The route's breaker and retry filters, in the order the gateway runs them. */
    private List<GatewayFilter> breakerAndRetry() {
        List<GatewayFilter> sorted = new ArrayList<>(route.getFilters());
        AnnotationAwareOrderComparator.sort(sorted);
        List<GatewayFilter> picked = new ArrayList<>();
        for (GatewayFilter f : sorted) {
            GatewayFilter delegate = f instanceof OrderedGatewayFilter o ? o.getDelegate() : f;
            boolean retry = delegate.getClass().getName().startsWith(RetryGatewayFilterFactory.class.getName());
            boolean breaker = !retry && delegate.getClass().getName().startsWith(RouteRetryTest.class.getName());
            if (retry || breaker) {
                picked.add(delegate);
            }
        }
        return picked;
    }

    private ServerWebExchange send(HttpMethod method, Function<ServerWebExchange, Mono<Void>> downstream) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, "/api/v1/ride/rides/1"));
        List<GatewayFilter> filters = breakerAndRetry();
        GatewayFilterChain[] chains = new GatewayFilterChain[filters.size() + 1];
        // Deferred like the real routing filter: each retry re-subscribes, which is one more call to the service.
        chains[filters.size()] = ex -> Mono.defer(() -> {
            downstreamCalls.incrementAndGet();
            return downstream.apply(ex);
        });
        for (int i = filters.size() - 1; i >= 0; i--) {
            GatewayFilter f = filters.get(i);
            GatewayFilterChain next = chains[i + 1];
            chains[i] = ex -> f.filter(ex, next);
        }
        chains[0].filter(exchange).block(Duration.ofSeconds(10));
        return exchange;
    }

    private static Function<ServerWebExchange, Mono<Void>> answers(HttpStatus status) {
        return ex -> {
            ex.getResponse().setStatusCode(status);
            return Mono.empty();
        };
    }

    @Test
    void theBreakerWrapsTheRetrySoTheRetryRunsInsideIt() {
        List<GatewayFilter> filters = breakerAndRetry();
        assertThat(filters).hasSize(2);
        assertThat(filters.get(0).getClass().getName()).startsWith(RouteRetryTest.class.getName());
        assertThat(filters.get(1).getClass().getName()).startsWith(RetryGatewayFilterFactory.class.getName());
    }

    @Test
    void aGetAnswered503IsRetriedTwiceAndTheServiceAnswerIsReturned() {
        ServerWebExchange ex = send(HttpMethod.GET, answers(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(downstreamCalls).hasValue(1 + RouteRetryConstants.RETRIES);
        assertThat(fallbacks).hasValue(0);
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aGetThatSucceedsOnTheSecondInstanceStopsRetrying() {
        AtomicInteger n = new AtomicInteger();
        ServerWebExchange ex = send(HttpMethod.GET,
                e -> answers(n.incrementAndGet() == 1 ? HttpStatus.BAD_GATEWAY : HttpStatus.OK).apply(e));

        assertThat(downstreamCalls).hasValue(2);
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void a500IsNotRetriedNowThatTheDefaultSeriesIsCleared() {
        send(HttpMethod.GET, answers(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(downstreamCalls).hasValue(1);
    }

    @Test
    void writesAreNeverRetried() {
        send(HttpMethod.POST, answers(HttpStatus.SERVICE_UNAVAILABLE));
        send(HttpMethod.PUT, e -> Mono.error(new ConnectException("refused")));

        assertThat(downstreamCalls).hasValue(2);
    }

    @Test
    void aRefusedConnectionIsRetriedAndOnlyThenTheFallbackAnswersOnce() {
        ServerWebExchange ex = send(HttpMethod.GET, e -> Mono.error(new ConnectException("refused")));

        assertThat(downstreamCalls).hasValue(1 + RouteRetryConstants.RETRIES);
        assertThat(fallbacks).hasValue(1);
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aTimedOutCallIsNotRetried() {
        send(HttpMethod.GET, e -> Mono.error(new TimeoutException("slow")));

        assertThat(downstreamCalls).hasValue(1);
        assertThat(fallbacks).hasValue(1);
    }

    @Test
    void anOpenBreakerAnswersWithItsFallbackOnceAndNeverCallsTheService() {
        breakerOpen = true;

        ServerWebExchange ex = send(HttpMethod.GET, answers(HttpStatus.OK));

        assertThat(downstreamCalls).hasValue(0);
        assertThat(fallbacks).hasValue(1);
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
