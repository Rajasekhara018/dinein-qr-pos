package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The public live-trip tracking route: GET only, declared before the general ride route, sent to RideService unchanged,
 * and limited per client address by its own limiter, which runs before the circuit breaker and works without a sign-in.
 */
class PublicTrackRouteTest {

    private static ConfigurableApplicationContext stubContext() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getBean(any(Class.class))).thenAnswer(inv -> RouteConfigTest.instantiate(inv.getArgument(0)));
        return ctx;
    }

    private static List<Route> routeList(RouteConfig rc) {
        RouteLocator locator = rc.railCabRoutes(new RouteLocatorBuilder(stubContext()));
        return locator.getRoutes().collectList().block();
    }

    private static Map<String, Route> routes(RouteConfig rc) {
        return routeList(rc).stream().collect(Collectors.toMap(Route::getId, r -> r));
    }

    private static RouteConfig withTrackLimiter(GatewayProperties props) {
        RouteConfig rc = RouteConfigTest.config(props, true, false);
        ReflectionTestUtils.setField(rc, "publicTrackRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "publicTrackIpKeyResolver", mock(KeyResolver.class));
        return rc;
    }

    private static boolean matches(Route route, HttpMethod method, String path) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(ex)).block());
    }

    @Test
    void theTrackRouteTakesOnlyGetOnTheExactPathAndPrecedesTheRideRoute() {
        RouteConfig rc = RouteConfigTest.config(new GatewayProperties(), true, false);
        List<String> ids = routeList(rc).stream().map(Route::getId).toList();
        assertThat(ids.indexOf(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID)).isGreaterThanOrEqualTo(0)
                .isLessThan(ids.indexOf("rider-ride-api"));

        Route track = routes(rc).get(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID);
        assertThat(track.getUri().toString()).isEqualTo("lb://REALBOOM-RIDESERVICE");
        assertThat(matches(track, HttpMethod.GET, RidePublicRouteConstants.PUBLIC_TRACK_PATH)).isTrue();
        for (HttpMethod m : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            assertThat(matches(track, m, RidePublicRouteConstants.PUBLIC_TRACK_PATH)).as(m.name()).isFalse();
        }
        assertThat(matches(track, HttpMethod.GET, "/api/v1/ride/public/track/other")).isFalse();
        assertThat(matches(track, HttpMethod.GET, "/api/v1/ride/rides/1")).isFalse();
        // a write to the tracking path is still a ride call (token required), never the public route
        assertThat(matches(routes(rc).get("rider-ride-api"), HttpMethod.POST, RidePublicRouteConstants.PUBLIC_TRACK_PATH)).isTrue();
    }

    @Test
    void theTrackLimiterIsAddedOnlyWhenEnabledAndRunsBeforeTheBreaker() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(RouteConfigTest.config(props, true, false))
                .get(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID).getFilters().size();

        Route limited = routes(withTrackLimiter(props)).get(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID);
        assertThat(limited.getFilters()).hasSize(plain + 1);
        List<String> names = limited.getFilters().stream().map(f -> f.toString().toLowerCase()).toList();
        int limiter = indexOf(names, "ratelimit");
        int breaker = indexOf(names, "[null,");
        assertThat(limiter).as(names.toString()).isGreaterThanOrEqualTo(0).isLessThan(breaker);

        GatewayProperties off = new GatewayProperties();
        off.getRateLimit().setEnabled(false);
        assertThat(routes(withTrackLimiter(off)).get(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID).getFilters())
                .hasSize(plain);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTrackRouteHasItsOwnBreakerAndFallback_neverTheRideApiBreaker() {
        Map<String, SpringCloudCircuitBreakerFilterFactory.Config> breakers = new java.util.HashMap<>();
        SpringCloudCircuitBreakerFilterFactory factory = mock(SpringCloudCircuitBreakerFilterFactory.class);
        when(factory.apply(anyString(), any(java.util.function.Consumer.class))).thenAnswer(inv -> {
            SpringCloudCircuitBreakerFilterFactory.Config config = new SpringCloudCircuitBreakerFilterFactory.Config();
            ((java.util.function.Consumer<SpringCloudCircuitBreakerFilterFactory.Config>) inv.getArgument(1)).accept(config);
            breakers.put(inv.getArgument(0), config);
            return (org.springframework.cloud.gateway.filter.GatewayFilter) (exchange, chain) -> chain.filter(exchange);
        });
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getBean(any(Class.class))).thenAnswer(inv -> SpringCloudCircuitBreakerFilterFactory.class
                .equals(inv.getArgument(0)) ? factory : RouteConfigTest.instantiate(inv.getArgument(0)));

        RouteConfigTest.config(new GatewayProperties(), true, false).railCabRoutes(new RouteLocatorBuilder(ctx))
                .getRoutes().collectList().block();

        SpringCloudCircuitBreakerFilterFactory.Config track = breakers.get(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID);
        SpringCloudCircuitBreakerFilterFactory.Config rideApi = breakers.get("rider-ride-api");
        assertThat(track.getName()).isEqualTo("rider-ride-public-track-cb")
                .isEqualTo(RidePublicRouteConstants.PUBLIC_TRACK_CIRCUIT_BREAKER);
        assertThat(track.getFallbackUri()).hasToString("forward:/fallback/rider-ride-public-track");
        assertThat(rideApi.getName()).isEqualTo("rider-ride-cb");
        assertThat(rideApi.getFallbackUri()).hasToString("forward:/fallback/rider-ride");
    }

    @Test
    void theTrackBreakerIsConfiguredLikeEveryOtherRouteBreaker() throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (var in = PublicTrackRouteTest.class.getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        String cb = RidePublicRouteConstants.PUBLIC_TRACK_CIRCUIT_BREAKER;
        assertThat(props.getProperty("resilience4j.circuitbreaker.instances." + cb + ".base-config")).isEqualTo("default");
        assertThat(props.getProperty("resilience4j.timelimiter.instances." + cb + ".base-config")).isEqualTo("default");
    }

    private static int indexOf(List<String> names, String part) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void theLimiterDefaultsToOnePerSecondWithABurstOfFive() {
        GatewayProperties props = new GatewayProperties();
        RedisRateLimiter.Config cfg = (RedisRateLimiter.Config) ReflectionTestUtils.getField(
                new RateLimiterConfig(props).publicTrackRateLimiter(), "defaultConfig");
        assertThat(cfg.getReplenishRate()).isEqualTo(1);
        assertThat(cfg.getBurstCapacity()).isEqualTo(5);

        props.getRateLimit().setPublicTrackReplenishRate(2);
        props.getRateLimit().setPublicTrackBurstCapacity(8);
        RedisRateLimiter.Config changed = (RedisRateLimiter.Config) ReflectionTestUtils.getField(
                new RateLimiterConfig(props).publicTrackRateLimiter(), "defaultConfig");
        assertThat(changed.getReplenishRate()).isEqualTo(2);
        assertThat(changed.getBurstCapacity()).isEqualTo(8);
    }

    @Test
    void theKeyIsAlwaysTheClientAddressInItsOwnBucketEvenWhenAUserIdIsSent() {
        KeyResolver resolver = new RateLimiterConfig(new GatewayProperties()).publicTrackIpKeyResolver();
        MockServerWebExchange forged = MockServerWebExchange.from(MockServerHttpRequest.get(RidePublicRouteConstants.PUBLIC_TRACK_PATH)
                .header("X-User-Id", "u-1").remoteAddress(new InetSocketAddress("203.0.113.9", 4000)).build());
        MockServerWebExchange none = MockServerWebExchange.from(MockServerHttpRequest.get(RidePublicRouteConstants.PUBLIC_TRACK_PATH).build());

        assertThat(resolver.resolve(forged).block()).isEqualTo("track-ip:203.0.113.9");
        assertThat(resolver.resolve(none).block()).isEqualTo("track-ip:unknown");
    }

    @Test
    void anAnonymousCallOverTheLimitIsRefusedWith429AndNeverForwarded() {
        KeyResolver resolver = new RateLimiterConfig(new GatewayProperties()).publicTrackIpKeyResolver();
        @SuppressWarnings("unchecked")
        RateLimiter<Object> limiter = mock(RateLimiter.class);
        AtomicReference<String> keySeen = new AtomicReference<>();
        when(limiter.isAllowed(anyString(), anyString())).thenAnswer(inv -> {
            keySeen.set(inv.getArgument(1));
            return Mono.just(new RateLimiter.Response(false, Map.of()));
        });
        var filter = new RequestRateLimiterGatewayFilterFactory(limiter, resolver)
                .apply(new RequestRateLimiterGatewayFilterFactory.Config());
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(RidePublicRouteConstants.PUBLIC_TRACK_PATH)
                .header(RidePublicRouteConstants.SHARE_TOKEN_HEADER, "tok")
                .remoteAddress(new InetSocketAddress("198.51.100.4", 5000)).build());
        ex.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID).uri("http://ride:1")
                        .predicate(e -> true).build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(ex, e -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(forwarded).isFalse();
        assertThat(keySeen.get()).isEqualTo("track-ip:198.51.100.4");
    }
}
