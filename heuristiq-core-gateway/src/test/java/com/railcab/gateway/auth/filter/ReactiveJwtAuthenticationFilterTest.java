package com.heuristiq.gateway.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.auth.provider.AuthProviderRouter;
import com.heuristiq.gateway.auth.provider.HmacJwtAuthProvider;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.auth.service.UserStatusCacheService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveJwtAuthenticationFilterTest {

    private static final String ADMIN_SECRET =
            "test-secret-key-for-unit-testing-minimum-256-bits-padding!!";
    private static final String CUSTOMER_SECRET =
            "customer-test-secret-key-for-unit-testing-min-256-bits!!";

    @Mock private UserStatusCacheService userStatusCacheService;

    private ReactiveJwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties();
        props.getAuth().setJwtSecret(ADMIN_SECRET);
        props.getAuth().setCustomerJwtSecret(CUSTOMER_SECRET);
        props.getInternal().setSecretKeys("test-internal-key-1,test-internal-key-2");
        props.getInternal().setGatewayForwardKey("test-gateway-forward-key");

        PathMatchersConfig pm = new PathMatchersConfig();
        pm.setIrctcHealth("/api/irctc/health/**");
        pm.setIrctcSimulator("/api/irctc/simulator/**");
        pm.setActuator("/actuator");

        HmacJwtAuthProvider authProvider = new HmacJwtAuthProvider(props);
        AuthProviderRouter authProviderRouter = new AuthProviderRouter(props, List.of(authProvider));

        filter = new ReactiveJwtAuthenticationFilter(
                authProviderRouter, props, pm,
                userStatusCacheService,
                new ObjectMapper(),
                Schedulers.boundedElastic());
    }

    /** Binds the real application.properties, so a missing or mistyped pathmatchers.* entry fails here. */
    @Test
    void canonicalPublicPathsFromRealConfigPassWithoutTokenAndProtectedOnesDoNot() throws Exception {
        java.util.Properties raw = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            raw.load(in);
        }
        PathMatchersConfig real = org.springframework.boot.context.properties.bind.Binder
                .get(new org.springframework.core.env.StandardEnvironment() {{
                    getPropertySources().addFirst(new org.springframework.core.env.PropertiesPropertySource("app", raw));
                }})
                .bind("pathmatchers", PathMatchersConfig.class).get();
        GatewayProperties props = new GatewayProperties();
        props.getAuth().setJwtSecret(ADMIN_SECRET);
        props.getAuth().setCustomerJwtSecret(CUSTOMER_SECRET);
        props.getInternal().setSecretKeys("test-internal-key-1,test-internal-key-2");
        props.getInternal().setGatewayForwardKey("test-gateway-forward-key");
        filter = new ReactiveJwtAuthenticationFilter(
                new AuthProviderRouter(props, List.of(new HmacJwtAuthProvider(props))), props, real,
                userStatusCacheService, new ObjectMapper(), Schedulers.boundedElastic());

        for (String path : new String[]{
                "/api/v1/ride/ws", "/api/v1/ride/ws-signal/abc123",
                "/api/v1/payment/payments/webhook", "/api/v1/payment/wallet/topup/webhook",
                "/api/v1/config/public/banners",
                "/api/v1/onboarding/driver/active-schema",
                "/api/v1/onboarding/driver/submissions/9/submit",
                "/api/v1/train/search", "/api/v1/train/public/x"}) {
            assertChainCalled(path, null);
        }
        for (String path : new String[]{
                "/api/v1/ride/rides/history", "/api/v1/customer/customers/1", "/api/v1/config/settings",
                "/api/v1/train/bookings", "/api/v1/onboarding/admin/onboarding-types",
                "/api/v1/payment/wallet/1/balance", "/api/v1/audit/events"}) {
            assertUnauthorized(path);
        }
    }

    // ── Public paths ─────────────────────────────────────────────────────────────
    // Local/customer/admin auth all moved onto UAM's unified /identity/auth/** surface this
    // migration, so admin-login/bookings-auth/customer-*/driver-* are no longer real gateway
    // public-path config in any profile (confirmed: no controller in BookingsAPI/AdminAPI maps to
    // any of fare/estimate, availability, track/public, driver/register, driver/otp, or
    // driver/profile/public anymore) -- those scenarios were removed along with the dead config.

    @Test
    void fallbackPath_passesWithoutToken() {
        assertChainCalled("/fallback/admin", null);
    }

    // ── OPTIONS preflight ─────────────────────────────────────────────────────────

    @Test
    void optionsPreflight_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/admin/v1/users").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
    }

    // ── Missing / malformed token ─────────────────────────────────────────────────

    @Test
    void theOrganizationHeaderIsSetFromTheTokenAndNeverTakenFromTheClient() {
        when(userStatusCacheService.isActive("u1", "MANAGER")).thenReturn(true);
        String token = io.jsonwebtoken.Jwts.builder().subject("u1").claim("role", "MANAGER").claim("tenantId", "org-7")
                .expiration(futureDate(3600)).signWith(signingKey()).compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Tenant-Id", "forged-org").build());
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        WebFilterChain chain = ex -> { seen.set(ex.getRequest().getHeaders().getFirst("X-Tenant-Id")); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals("org-7", seen.get());
    }

    @Test
    void aTokenWithoutAnOrganizationForwardsNoOrganizationHeaderEvenIfTheClientSentOne() {
        when(userStatusCacheService.isActive("u1", "MANAGER")).thenReturn(true);
        String token = buildToken("u1", "MANAGER", futureDate(3600));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Tenant-Id", "forged-org").build());
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>("unset");
        WebFilterChain chain = ex -> { seen.set(ex.getRequest().getHeaders().getFirst("X-Tenant-Id")); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNull(seen.get(), "chain must be reached, with no organization header");
    }

    @Test
    void theOrgWideHeaderIsSetOnlyFromTheTokenClaimAndNeverTakenFromTheClient() {
        when(userStatusCacheService.isActive("u1", "MANAGER")).thenReturn(true);
        String orgWideToken = io.jsonwebtoken.Jwts.builder().subject("u1").claim("role", "MANAGER").claim("tenantId", "org-7").claim("orgWide", true)
                .expiration(futureDate(3600)).signWith(signingKey()).compact();
        String ordinaryToken = buildToken("u1", "MANAGER", futureDate(3600));
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>("unset");
        WebFilterChain chain = ex -> { seen.set(ex.getRequest().getHeaders().getFirst("X-Org-Wide")); return Mono.empty(); };

        StepVerifier.create(filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgWideToken).build()), chain)).verifyComplete();
        assertEquals("true", seen.get());

        StepVerifier.create(filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ordinaryToken).header("X-Org-Wide", "true").build()), chain)).verifyComplete();
        assertNull(seen.get());
    }

    @Test
    void missingBearerToken_returns401() {
        assertUnauthorized("/api/admin/v1/dashboard");
    }

    @Test
    void malformedToken_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt.token")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void expiredToken_returns401() {
        String token = buildToken("user-1", "MANAGER", pastDate(3600));
        MockServerWebExchange exchange = bearerExchange("/api/admin/v1/dashboard", token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ── Admin token validation ─────────────────────────────────────────────────────

    @Test
    void validAdminToken_activeUser_injectsHeadersAndPassesThrough() {
        when(userStatusCacheService.isActive("admin-123", "MANAGER")).thenReturn(true);

        String token = buildToken("admin-123", "MANAGER", futureDate(3600));
        MockServerWebExchange exchange = bearerExchange("/api/admin/v1/dashboard", token);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals("admin-123", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID));
            assertEquals("MANAGER",   ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE));
            assertEquals("test-gateway-forward-key",
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
        verify(userStatusCacheService).isActive("admin-123", "MANAGER");
    }

    @Test
    void validAdminToken_inactiveUser_returns401() {
        when(userStatusCacheService.isActive("admin-locked", "SUPER_ADMIN")).thenReturn(false);

        String token = buildToken("admin-locked", "SUPER_ADMIN", futureDate(3600));
        MockServerWebExchange exchange = bearerExchange("/api/admin/v1/dashboard", token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ── Customer token validation (BookingsAPI secret) ─────────────────────────────

    @Test
    void validCustomerToken_signedWithCustomerSecret_activeCustomer_passesThrough() {
        when(userStatusCacheService.isActive("customer-456", "CUSTOMER")).thenReturn(true);

        String token = buildTokenWith("customer-456", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET);
        MockServerWebExchange exchange = bearerExchange("/api/bookings/rides/123", token);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals("customer-456", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID));
            assertEquals("CUSTOMER",     ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE));
            assertEquals("test-gateway-forward-key",
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get(), "Customer token signed with customer secret must be accepted");
        verify(userStatusCacheService).isActive("customer-456", "CUSTOMER");
    }

    @Test
    void validCustomerToken_activeCustomer_injectsHeadersAndPassesThrough() {
        when(userStatusCacheService.isActive("customer-456", "CUSTOMER")).thenReturn(true);

        String token = buildToken("customer-456", "CUSTOMER", futureDate(3600));
        MockServerWebExchange exchange = bearerExchange("/api/bookings/rides/123", token);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals("customer-456", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID));
            assertEquals("CUSTOMER",     ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE));
            assertEquals("test-gateway-forward-key",
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
        verify(userStatusCacheService).isActive("customer-456", "CUSTOMER");
    }

    @Test
    void validCustomerToken_inactiveCustomer_returns401() {
        when(userStatusCacheService.isActive("banned-customer", "CUSTOMER")).thenReturn(false);

        String token = buildToken("banned-customer", "CUSTOMER", futureDate(3600));
        MockServerWebExchange exchange = bearerExchange("/api/bookings/rides/123", token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ── Driver token validation ────────────────────────────────────────────────────

    @Test
    void validDriverToken_activeApprovedDriver_injectsHeadersAndPassesThrough() {
        when(userStatusCacheService.isActive("driver-789", "DRIVER")).thenReturn(true);

        String token = buildTokenWith("driver-789", "DRIVER", futureDate(3600), CUSTOMER_SECRET);
        MockServerWebExchange exchange = bearerExchange("/api/bookings/driver/location", token);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals("driver-789", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID));
            assertEquals("DRIVER",     ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE));
            assertEquals("test-gateway-forward-key",
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
        verify(userStatusCacheService).isActive("driver-789", "DRIVER");
    }

    @Test
    void validDriverToken_inactiveDriver_returns401() {
        when(userStatusCacheService.isActive("suspended-driver", "DRIVER")).thenReturn(false);

        String token = buildTokenWith("suspended-driver", "DRIVER", futureDate(3600), CUSTOMER_SECRET);
        MockServerWebExchange exchange = bearerExchange("/api/bookings/driver/location", token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void driverAndCustomerRoles_callCacheServiceWithCorrectRole() {
        when(userStatusCacheService.isActive("driver-001", "DRIVER")).thenReturn(true);
        when(userStatusCacheService.isActive("cust-001", "CUSTOMER")).thenReturn(true);

        StepVerifier.create(filter.filter(
                bearerExchange("/api/bookings/driver/trips",
                        buildTokenWith("driver-001", "DRIVER", futureDate(3600), CUSTOMER_SECRET)),
                ex -> Mono.empty())).verifyComplete();

        StepVerifier.create(filter.filter(
                bearerExchange("/api/bookings/rides/123",
                        buildTokenWith("cust-001", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET)),
                ex -> Mono.empty())).verifyComplete();

        verify(userStatusCacheService).isActive("driver-001", "DRIVER");
        verify(userStatusCacheService).isActive("cust-001", "CUSTOMER");
    }

    @Test
    void expiredDriverToken_returns401_withoutHittingCache() {
        String token = buildTokenWith("driver-001", "DRIVER", pastDate(3600), CUSTOMER_SECRET);
        MockServerWebExchange exchange = bearerExchange("/api/bookings/driver/location", token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(userStatusCacheService);
    }

    // ── Internal service key ───────────────────────────────────────────────────────

    @Test
    void anIncomingServiceTokenHeaderIsStrippedBeforeForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/internal/sync")
                        .header(GatewayConstants.INTERNAL_HEADER_NAME, "test-internal-key-1")
                        .header(GatewayConstants.X_RIDER_SERVICE_TOKEN, "stolen-service-token")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertNull(ex.getRequest().getHeaders().getFirst(GatewayConstants.X_RIDER_SERVICE_TOKEN));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(chainCalled.get());
    }

    @Test
    void validInternalKey_injectsInternalRoleAndPassesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/internal/sync")
                        .header(GatewayConstants.INTERNAL_HEADER_NAME, "test-internal-key-1")
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals(GatewayConstants.INTERNAL_SERVICE,
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID));
            assertEquals(GatewayConstants.INTERNAL_SERVICE_ROLE,
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE));
            assertEquals("test-gateway-forward-key",
                    ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN));
            // The inbound X-Internal-Secret-Key must be stripped before forwarding
            assertNull(ex.getRequest().getHeaders().getFirst(GatewayConstants.INTERNAL_HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
        verifyNoInteractions(userStatusCacheService);
    }

    @Test
    void invalidInternalKey_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/internal/sync")
                        .header(GatewayConstants.INTERNAL_HEADER_NAME, "wrong-key")
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ── Header stripping (injection prevention) ────────────────────────────────────

    @Test
    void publicPath_clientSentXUserId_isStrippedBeforeChain() {
        // A malicious client tries to impersonate an admin on a public path.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/irctc/health/check")
                        .header(GatewayConstants.X_USER_ID, "hacker-admin-id")
                        .header(GatewayConstants.X_USER_ROLE, "SUPER_ADMIN")
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertNull(ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID),
                    "X-User-Id must be stripped even on public paths");
            assertNull(ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE),
                    "X-User-Role must be stripped even on public paths");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
    }

    @Test
    void publicPath_clientSentGatewayToken_isStripped() {
        // A malicious client sends a fake X-Gateway-Token hoping downstream services accept it.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/irctc/health/check")
                        .header(GatewayConstants.X_GATEWAY_TOKEN, "fake-gateway-token")
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertNull(ex.getRequest().getHeaders().getFirst(GatewayConstants.X_GATEWAY_TOKEN),
                    "X-Gateway-Token must be stripped even on public paths");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
    }

    @Test
    void authenticatedPath_clientSentFakeUserId_isReplacedWithTokenIdentity() {
        // Client sends both a valid JWT AND a fake X-User-Id — the fake header must be replaced
        // by the identity extracted from the validated token.
        when(userStatusCacheService.isActive("real-admin-id", "MANAGER")).thenReturn(true);

        String token = buildToken("real-admin-id", "MANAGER", futureDate(3600));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(GatewayConstants.X_USER_ID,   "hacker-injected-id")
                        .header(GatewayConstants.X_USER_ROLE, "SUPER_ADMIN")
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            assertEquals("real-admin-id", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID),
                    "Identity must come from the token, not the client header");
            assertEquals("MANAGER", ex.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ROLE),
                    "Role must come from the token, not the client header");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private void assertChainCalled(String path, String bearerToken) {
        MockServerWebExchange exchange = bearerToken != null
                ? bearerExchange(path, bearerToken)
                : MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get(), "Expected chain to be called for path=" + path);
    }

    private void assertUnauthorized(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private MockServerWebExchange bearerExchange(String path, String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
    }

    private String buildToken(String userId, String role, Date expiry) {
        return buildTokenWith(userId, role, expiry, ADMIN_SECRET);
    }

    private String buildTokenWith(String userId, String role, Date expiry, String secret) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private Date futureDate(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000);
    }

    private Date pastDate(long seconds) {
        return new Date(System.currentTimeMillis() - seconds * 1000);
    }

    @Test
    void anErrorFromTheRouteAfterTheResponseWasSentIsNotTurnedIntoASecondErrorResponse() {
        when(userStatusCacheService.isActive("u1", "MANAGER")).thenReturn(true);
        String token = io.jsonwebtoken.Jwts.builder().subject("u1").claim("role", "MANAGER")
                .expiration(futureDate(3600)).signWith(signingKey()).compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/v1/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        WebFilterChain chain = ex -> ex.getResponse().setComplete()
                .then(Mono.error(new UnsupportedOperationException("response already committed")));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(exchange.getResponse().isCommitted());
    }
}
