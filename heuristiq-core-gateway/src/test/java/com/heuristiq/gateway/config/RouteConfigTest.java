package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the real route table against a stub application context (filter factories are instantiated, not started)
 * and checks route ids, target URIs for every discovery mode and the version-aware path rewrite.
 */
class RouteConfigTest {

    /** Context whose getBean(Class) instantiates the requested gateway factory, mocking constructor arguments. */
    private static ConfigurableApplicationContext stubContext() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getBean(any(Class.class))).thenAnswer(inv -> instantiate(inv.getArgument(0)));
        return ctx;
    }

    static Object instantiate(Class<?> type) throws Exception {
        if (type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
            return Mockito.mock(type);
        }
        for (Constructor<?> c : type.getConstructors()) {
            if (c.getParameterCount() == 0) {
                return c.newInstance();
            }
        }
        Constructor<?> c = type.getConstructors()[0];
        Object[] args = new Object[c.getParameterCount()];
        Class<?>[] types = c.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            args[i] = types[i].isPrimitive() ? null : Mockito.mock(types[i]);
        }
        return c.newInstance(args);
    }

    static RouteConfig config(GatewayProperties props, boolean discovery, boolean riderDiscovery) {
        RouteConfig rc = new RouteConfig(props);
        ReflectionTestUtils.setField(rc, "discoveryEnabled", discovery);
        ReflectionTestUtils.setField(rc, "riderServicesDiscoveryEnabled", riderDiscovery);
        ReflectionTestUtils.setField(rc, "adminApiStaticUrl", "http://admin:1");
        ReflectionTestUtils.setField(rc, "bookingsApiStaticUrl", "http://bookings:1");
        ReflectionTestUtils.setField(rc, "registryStaticUrl", "http://registry:1");
        ReflectionTestUtils.setField(rc, "irctcApiStaticUrl", "http://irctc:1");
        ReflectionTestUtils.setField(rc, "uamApiStaticUrl", "http://uam:1");
        ReflectionTestUtils.setField(rc, "dmsApiStaticUrl", "http://dms:1");
        ReflectionTestUtils.setField(rc, "nmsApiStaticUrl", "http://nms:1");
        ReflectionTestUtils.setField(rc, "searchApiStaticUrl", "http://search:1");
        ReflectionTestUtils.setField(rc, "paymentApiStaticUrl", "http://pay:1");
        ReflectionTestUtils.setField(rc, "paymentApiMtlsUrl", "https://pay:2");
        ReflectionTestUtils.setField(rc, "nmsApiKey", "k");
        for (String f : List.of("riderPricingApiStaticUrl", "riderLocationApiStaticUrl", "riderDispatchApiStaticUrl",
                "riderRideApiStaticUrl", "riderPaymentApiStaticUrl", "riderCustomerApiStaticUrl",
                "riderWorkflowApiStaticUrl", "riderOnboardingApiStaticUrl", "matrimonyApiStaticUrl", "foodApiStaticUrl",
                "riderTrainApiStaticUrl", "riderConfigApiStaticUrl", "riderAuditApiStaticUrl",
                "riderReportsApiStaticUrl", "riderWalletApiStaticUrl", "riderAccountingApiStaticUrl", "riderDriverEarningsApiStaticUrl")) {
            ReflectionTestUtils.setField(rc, f, "");
        }
        return rc;
    }

    private static Map<String, Route> routes(RouteConfig rc) {
        RouteLocator locator = rc.railCabRoutes(new RouteLocatorBuilder(stubContext()));
        List<Route> list = locator.getRoutes().collectList().block();
        return list.stream().collect(Collectors.toMap(Route::getId, r -> r));
    }

    @Test
    void buildsEveryRouteWithUniqueIdsAndTheExpectedTargets() {
        Map<String, Route> routes = routes(config(new GatewayProperties(), true, false));

        assertThat(routes).hasSizeGreaterThan(60);
        assertThat(routes).containsKeys("uam-api", "uam-auth-api", "uam-login-api", "admin-api", "rider-ride-api", "pay-api",
                "bookings-api", "nms-api", "matrimony-api", "rider-audit-api", "rider-reports-api", "rider-franchise-api", "uam-javadoc",
                "rider-accounting-api", "rider-accounting-docs", "rider-accounting-javadoc");
        assertThat(routes.get("uam-api").getUri().toString()).isEqualTo("lb://REALBOOM-UAM");
        assertThat(routes.get("admin-api").getUri().toString()).isEqualTo("lb://REALBOOM-ADMIN-API");
        // Bookings never registers with the registry: always its static URL.
        assertThat(routes.get("bookings-api").getUri().toString()).isEqualTo("http://bookings:1");
        assertThat(routes.get("registry-javadoc").getUri().toString()).isEqualTo("http://registry:1");
        assertThat(routes.get("rider-ride-api").getUri().toString()).isEqualTo("lb://REALBOOM-RIDESERVICE");
        assertThat(routes.get("rider-franchise-api").getUri().toString()).isEqualTo("lb://REALBOOM-FRANCHISESERVICE");
        assertThat(routes.get("rider-accounting-api").getUri().toString()).isEqualTo("lb://REALBOOM-ACCOUNTINGSERVICE");
    }

    @Test
    void withoutDiscoveryRoutesUseStaticUrlsAndLocalPorts() {
        Map<String, Route> routes = routes(config(new GatewayProperties(), false, false));

        assertThat(routes.get("uam-api").getUri().toString()).isEqualTo("http://uam:1");
        assertThat(routes.get("nms-api").getUri().toString()).isEqualTo("http://nms:1");
        assertThat(routes.get("pay-api").getUri().toString()).isEqualTo("http://pay:1");
        assertThat(routes.get("rider-ride-api").getUri().toString()).isEqualTo("http://localhost:9115");
        assertThat(routes.get("rider-config-api").getUri().toString()).isEqualTo("http://localhost:9102");
        assertThat(routes.get("rider-accounting-api").getUri().toString()).isEqualTo("http://localhost:9126");
    }

    @Test
    void riderServiceDiscoveryForcesRegistryUrisButMatrimonyStaysStaticWhenConfigured() {
        RouteConfig rc = config(new GatewayProperties(), false, true);
        ReflectionTestUtils.setField(rc, "matrimonyApiStaticUrl", "http://matrimony:9");
        ReflectionTestUtils.setField(rc, "riderPricingApiStaticUrl", "http://ignored:1");

        Map<String, Route> routes = routes(rc);

        assertThat(routes.get("rider-pricing-api").getUri().toString()).isEqualTo("lb://REALBOOM-PRICINGSERVICE");
        assertThat(routes.get("matrimony-api").getUri().toString()).isEqualTo("http://matrimony:9");
    }

    @Test
    void configuredRiderStaticUrlWinsWhenRiderDiscoveryIsOff() {
        RouteConfig rc = config(new GatewayProperties(), true, false);
        ReflectionTestUtils.setField(rc, "riderDispatchApiStaticUrl", "http://dispatch:5");

        assertThat(routes(rc).get("rider-dispatch-api").getUri().toString()).isEqualTo("http://dispatch:5");
    }

    @Test
    void matrimonyWithoutStaticUrlFallsBackToTheRegistryOrLocalPort() {
        assertThat(routes(config(new GatewayProperties(), true, false)).get("matrimony-api").getUri().toString())
                .isEqualTo("lb://MATRIMONIAL-API");
        assertThat(routes(config(new GatewayProperties(), false, false)).get("matrimony-api").getUri().toString())
                .isEqualTo("http://localhost:9120");
    }

    @Test
    void webSocketRoutesUseTheWebSocketScheme() {
        Map<String, Route> lb = routes(config(new GatewayProperties(), true, false));
        assertThat(lb.get("nms-in-app-websocket").getUri().toString()).isEqualTo("lb:ws://REALBOOM-NMS");
        assertThat(lb.get("rider-ride-websocket").getUri().toString()).isEqualTo("lb:ws://REALBOOM-RIDESERVICE");
        assertThat(lb.get("matrimony-websocket").getUri().toString()).isEqualTo("lb:ws://MATRIMONIAL-API");

        Map<String, Route> st = routes(config(new GatewayProperties(), false, false));
        assertThat(st.get("nms-in-app-websocket").getUri().toString()).isEqualTo("ws://nms:1");
        assertThat(st.get("rider-ride-websocket").getUri().toString()).isEqualTo("ws://localhost:9115");
        assertThat(st.get("matrimony-websocket").getUri().toString()).isEqualTo("ws://localhost:9120");
    }

    @Test
    void mtlsSendsPaymentTrafficToTheHttpsUrl() {
        RouteConfig rc = config(new GatewayProperties(), true, false);
        ReflectionTestUtils.setField(rc, "mtlsEnabled", true);

        assertThat(routes(rc).get("pay-api").getUri().toString()).isEqualTo("https://pay:2");
    }

    @Test
    void rateLimiterFiltersAreAddedOnlyWhenEnabledAndBeansExist() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        RouteConfig without = config(props, true, false);
        int plain = routes(without).get("uam-api").getFilters().size();

        RouteConfig with = config(props, true, false);
        ReflectionTestUtils.setField(with, "apiRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(with, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(with, "userOrIpKeyResolver", mock(KeyResolver.class));
        ReflectionTestUtils.setField(with, "authIpKeyResolver", mock(KeyResolver.class));
        Map<String, Route> limited = routes(with);

        assertThat(limited.get("uam-api").getFilters().size()).isGreaterThan(plain);
        // uam-api carries two extra path guards (internal, actuator) that the auth route does not
        assertThat(limited.get("uam-auth-api").getFilters().size()).isEqualTo(plain - 2 + 1);

        GatewayProperties off = new GatewayProperties();
        off.getRateLimit().setEnabled(false);
        RouteConfig disabled = config(off, true, false);
        ReflectionTestUtils.setField(disabled, "apiRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(disabled, "userOrIpKeyResolver", mock(KeyResolver.class));
        assertThat(routes(disabled).get("uam-api").getFilters().size()).isEqualTo(plain);
    }

    private static RouteConfig withLoginLimiters(GatewayProperties props) {
        RouteConfig rc = config(props, true, false);
        ReflectionTestUtils.setField(rc, "loginRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "loginGlobalRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "loginIpKeyResolver", mock(KeyResolver.class));
        ReflectionTestUtils.setField(rc, "loginGlobalKeyResolver", mock(KeyResolver.class));
        return rc;
    }

    @Test
    void thePasswordLoginRouteIsDeclaredBeforeTheAuthSurfaceAndOnlyTakesPost() {
        GatewayProperties props = new GatewayProperties();
        RouteLocator locator = config(props, true, false).railCabRoutes(new RouteLocatorBuilder(stubContext()));
        List<String> ids = locator.getRoutes().map(Route::getId).collectList().block();

        assertThat(ids.indexOf("uam-login-api")).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf("uam-auth-api"));
        Route login = routes(config(props, true, false)).get("uam-login-api");
        assertThat(login.getUri().toString()).isEqualTo("lb://REALBOOM-UAM");
        MockServerWebExchange post = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/login").build());
        MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/uam/identity/auth/login").build());
        MockServerWebExchange refresh = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/refresh").build());
        assertThat(Mono.from(login.getPredicate().apply(post)).block()).isTrue();
        assertThat(Mono.from(login.getPredicate().apply(get)).block()).isFalse();
        assertThat(Mono.from(login.getPredicate().apply(refresh)).block()).isFalse();
    }

    @Test
    void theLimitersRunBeforeTheCircuitBreakerOnTheLoginAndAuthRoutes() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        RouteConfig rc = withLoginLimiters(props);
        ReflectionTestUtils.setField(rc, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "authIpKeyResolver", mock(KeyResolver.class));
        Map<String, Route> routes = routes(rc);

        for (String id : List.of("uam-login-api", "uam-auth-api")) {
            List<String> names = routes.get(id).getFilters().stream().map(f -> f.toString().toLowerCase()).toList();
            int firstLimiter = indexOfFirst(names, "ratelimit");
            int breaker = indexOfFirst(names, CIRCUIT_BREAKER_FILTER);
            assertThat(firstLimiter).as(id + " has a rate limiter: " + names).isGreaterThanOrEqualTo(0);
            assertThat(breaker).as(id + " has a circuit breaker: " + names).isGreaterThanOrEqualTo(0);
            assertThat(firstLimiter).as(id + " limiter before breaker: " + names).isLessThan(breaker);
        }
        // every other route keeps its order: breaker first
        List<String> uamApi = routes(withApiLimiter(props)).get("uam-api").getFilters().stream().map(f -> f.toString().toLowerCase()).toList();
        assertThat(indexOfFirst(uamApi, CIRCUIT_BREAKER_FILTER)).isLessThan(indexOfFirst(uamApi, "ratelimit"));
    }

    /** The circuit breaker filter has no name of its own: Spring Cloud Gateway prints it as "[null, order = 0]". */
    private static final String CIRCUIT_BREAKER_FILTER = "[null,";

    private static RouteConfig withApiLimiter(GatewayProperties props) {
        RouteConfig rc = config(props, true, false);
        ReflectionTestUtils.setField(rc, "apiRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "userOrIpKeyResolver", mock(KeyResolver.class));
        return rc;
    }

    private static int indexOfFirst(List<String> names, String part) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void loginGetsTwoLimitersWhenTheLoginBeansExist() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-login-api").getFilters().size();

        assertThat(routes(withLoginLimiters(props)).get("uam-login-api").getFilters().size()).isEqualTo(plain + 2);
    }

    @Test
    void loginKeepsTheAuthLimitWhenTheLoginLimitersAreMissing() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-login-api").getFilters().size();
        RouteConfig authOnly = config(props, true, false);
        ReflectionTestUtils.setField(authOnly, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(authOnly, "authIpKeyResolver", mock(KeyResolver.class));

        assertThat(routes(authOnly).get("uam-login-api").getFilters().size()).isEqualTo(plain + 1);
    }

    @Test
    void loginHasNoLimiterWhenRateLimitingIsOff() {
        GatewayProperties off = new GatewayProperties();
        off.getRateLimit().setEnabled(false);
        int plain = routes(config(off, true, false)).get("uam-login-api").getFilters().size();

        assertThat(routes(withLoginLimiters(off)).get("uam-login-api").getFilters().size()).isEqualTo(plain);
    }

    // ---- token refresh route ---------------------------------------------------------------------------------------

    @Test
    void theRefreshRouteIsDeclaredBeforeTheAuthSurfaceAndOnlyTakesPostToRefresh() {
        GatewayProperties props = new GatewayProperties();
        List<String> ids = config(props, true, false).railCabRoutes(new RouteLocatorBuilder(stubContext()))
                .getRoutes().map(Route::getId).collectList().block();

        assertThat(ids.indexOf("uam-refresh-api")).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf("uam-auth-api"));
        Route refresh = routes(config(props, true, false)).get("uam-refresh-api");
        assertThat(refresh.getUri().toString()).isEqualTo("lb://REALBOOM-UAM");
        MockServerWebExchange post = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/refresh").build());
        MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/uam/identity/auth/refresh").build());
        MockServerWebExchange login = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/login").build());
        assertThat(Mono.from(refresh.getPredicate().apply(post)).block()).isTrue();
        assertThat(Mono.from(refresh.getPredicate().apply(get)).block()).isFalse();
        assertThat(Mono.from(refresh.getPredicate().apply(login)).block()).isFalse();
    }

    @Test
    void refreshUsesItsOwnLimiterBeforeTheCircuitBreaker() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-refresh-api").getFilters().size();
        RouteConfig rc = config(props, true, false);
        RedisRateLimiter refreshLimiter = mock(RedisRateLimiter.class);
        ReflectionTestUtils.setField(rc, "refreshRateLimiter", refreshLimiter);
        ReflectionTestUtils.setField(rc, "refreshIpKeyResolver", mock(KeyResolver.class));
        ReflectionTestUtils.setField(rc, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "authIpKeyResolver", mock(KeyResolver.class));

        Route refresh = routes(rc).get("uam-refresh-api");
        List<String> names = refresh.getFilters().stream().map(f -> f.toString().toLowerCase()).toList();
        assertThat(refresh.getFilters()).hasSize(plain + 1);
        assertThat(indexOfFirst(names, "ratelimit")).isGreaterThanOrEqualTo(0).isLessThan(indexOfFirst(names, CIRCUIT_BREAKER_FILTER));
    }

    @Test
    void refreshKeepsTheAuthLimitWhenItsOwnLimiterIsMissing() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-refresh-api").getFilters().size();
        RouteConfig authOnly = config(props, true, false);
        ReflectionTestUtils.setField(authOnly, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(authOnly, "authIpKeyResolver", mock(KeyResolver.class));

        assertThat(routes(authOnly).get("uam-refresh-api").getFilters()).hasSize(plain + 1);
    }

    @Test
    void refreshHasNoLimiterWhenRateLimitingIsOff() {
        GatewayProperties off = new GatewayProperties();
        off.getRateLimit().setEnabled(false);
        int plain = routes(config(off, true, false)).get("uam-refresh-api").getFilters().size();
        RouteConfig rc = config(off, true, false);
        ReflectionTestUtils.setField(rc, "refreshRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "refreshIpKeyResolver", mock(KeyResolver.class));

        assertThat(routes(rc).get("uam-refresh-api").getFilters()).hasSize(plain);
    }

    // ---- public sign-up route --------------------------------------------------------------------------------------

    @Test
    void theSignUpRouteIsDeclaredBeforeTheAuthSurfaceAndOnlyTakesPostToRegister() {
        GatewayProperties props = new GatewayProperties();
        List<String> ids = config(props, true, false).railCabRoutes(new RouteLocatorBuilder(stubContext()))
                .getRoutes().map(Route::getId).collectList().block();

        assertThat(ids.indexOf("uam-register-api")).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf("uam-auth-api"));
        Route register = routes(config(props, true, false)).get("uam-register-api");
        assertThat(register.getUri().toString()).isEqualTo("lb://REALBOOM-UAM");
        MockServerWebExchange post = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/register").build());
        MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/uam/identity/auth/register").build());
        MockServerWebExchange otp = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/uam/identity/auth/phone/send-otp").build());
        assertThat(Mono.from(register.getPredicate().apply(post)).block()).isTrue();
        assertThat(Mono.from(register.getPredicate().apply(get)).block()).isFalse();
        assertThat(Mono.from(register.getPredicate().apply(otp)).block()).isFalse();
    }

    @Test
    void signUpKeepsTheAuthLimitAndAddsItsHourlyLimitBothBeforeTheCircuitBreaker() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-register-api").getFilters().size();
        RouteConfig rc = config(props, true, false);
        ReflectionTestUtils.setField(rc, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "authIpKeyResolver", mock(KeyResolver.class));
        ReflectionTestUtils.setField(rc, "registerRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "registerIpKeyResolver", mock(KeyResolver.class));

        Route register = routes(rc).get("uam-register-api");
        List<String> names = register.getFilters().stream().map(f -> f.toString().toLowerCase()).toList();
        assertThat(register.getFilters()).hasSize(plain + 2);
        assertThat(names.stream().filter(n -> n.contains("ratelimit")).count()).isEqualTo(2);
        assertThat(names.lastIndexOf(names.stream().filter(n -> n.contains("ratelimit")).reduce((a, b) -> b).orElseThrow()))
                .isLessThan(indexOfFirst(names, CIRCUIT_BREAKER_FILTER));
    }

    @Test
    void signUpKeepsTheAuthLimitWhenItsOwnLimiterIsMissing() {
        GatewayProperties props = new GatewayProperties();
        props.getRateLimit().setEnabled(true);
        int plain = routes(config(props, true, false)).get("uam-register-api").getFilters().size();
        RouteConfig authOnly = config(props, true, false);
        ReflectionTestUtils.setField(authOnly, "authRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(authOnly, "authIpKeyResolver", mock(KeyResolver.class));

        assertThat(routes(authOnly).get("uam-register-api").getFilters()).hasSize(plain + 1);
    }

    @Test
    void signUpHasNoLimiterWhenRateLimitingIsOff() {
        GatewayProperties off = new GatewayProperties();
        off.getRateLimit().setEnabled(false);
        int plain = routes(config(off, true, false)).get("uam-register-api").getFilters().size();
        RouteConfig rc = config(off, true, false);
        ReflectionTestUtils.setField(rc, "registerRateLimiter", mock(RedisRateLimiter.class));
        ReflectionTestUtils.setField(rc, "registerIpKeyResolver", mock(KeyResolver.class));

        assertThat(routes(rc).get("uam-register-api").getFilters()).hasSize(plain);
    }

    // ---- version-aware rewrite -------------------------------------------------------------------------------------

    private static String rewrite(String gatewayPrefix, String path) {
        RouteConfig rc = config(new GatewayProperties(), true, false);
        GatewayFilter filter = ReflectionTestUtils.invokeMethod(rc, "versionAwareRewrite", gatewayPrefix);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seen.set(ex);
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get(path).build()), chain).block();
        return seen.get().getRequest().getURI().getRawPath();
    }

    @Test
    void versionAfterServicePrefixIsMovedBeforeItAndServiceSegmentInserted() {
        assertThat(rewrite("/api/payment", "/api/payment/v1/charge")).isEqualTo("/api/v1/payments/charge");
        assertThat(rewrite("/api/uam", "/api/uam/v3/users")).isEqualTo("/api/v3/uam/users");
    }

    @Test
    void versionBeforeServicePrefixIsKept() {
        assertThat(rewrite("/api/uam", "/api/v2/uam/users/1")).isEqualTo("/api/v2/uam/users/1");
        assertThat(rewrite("/api/nms", "/api/v1/nms/send")).isEqualTo("/api/v1/nms/send");
    }

    @Test
    void missingVersionDefaultsToV1() {
        assertThat(rewrite("/api/bookings", "/api/bookings/trips")).isEqualTo("/api/v1/trips");
        assertThat(rewrite("/api/irctc", "/api/irctc")).isEqualTo("/api/v1");
        assertThat(rewrite("/api/search", "/api/search/q/x")).isEqualTo("/api/v1/q/x");
    }

    @Test
    void adminPathCarriesTheAdminSegmentExactlyOnceAfterTheVersion() {
        String once = rewrite("/api/admin", "/api/admin/dashboard/summary");
        String twice = rewrite("/api/admin", "/api/admin/admin/dashboard/summary");

        assertThat(once).isEqualTo(twice).startsWith("/api/v1/admin/");
        assertThat(rewrite("/api/admin", "/api/admin")).startsWith("/api/v1/admin");
    }

    @Test
    void unknownPrefixUsesTheDefensiveFallback() {
        assertThat(rewrite("/api/zzz", "/api/zzz/foo/bar")).isEqualTo("/api/v1/foo/bar");
        assertThat(rewrite("/api/zzz", "/api/zzz")).isEqualTo("/api/v1");
        assertThat(rewrite("/x", "/x/y")).isEqualTo("/api/v1/y");
    }
}
