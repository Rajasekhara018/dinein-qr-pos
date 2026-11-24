package com.heuristiq.gateway.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.auth.provider.AuthProviderRouter;
import com.heuristiq.gateway.auth.provider.CallerDetails;
import com.heuristiq.gateway.auth.provider.IAuthProvider;
import com.heuristiq.gateway.auth.provider.TokenVerification;
import com.heuristiq.gateway.auth.util.AuthErrorMapping;
import com.heuristiq.gateway.auth.util.SecurityUtils;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.common.constants.MessageConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.auth.service.UserStatusCacheService;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Primary gateway security filter — wired into {@code GatewaySecurityConfig}'s security chain via
 * {@code addFilterBefore(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)}, positioned after
 * Spring Security's own CORS filter and before its AUTHENTICATION phase.
 *
 * <p><b>Deliberately not a {@code @Component}</b>: this class implements {@link WebFilter}, and
 * Spring Boot's reactive web autoconfiguration registers every {@code WebFilter} bean in the
 * application context as an independent, top-level global filter — regardless of whether that
 * same instance is also wired into a {@code SecurityWebFilterChain} via {@code addFilterBefore}.
 * Making this a {@code @Component} previously caused it to run twice per request: once correctly
 * ordered inside the security chain (after CORS), and once again as a standalone global filter at
 * {@code getOrder() == -100} — the same order Spring Boot's own {@code WebFilterChainProxy} (which
 * hosts the security chain, including the CORS filter) registers at. Depending on bean-registration
 * order, the standalone copy of this filter could run *before* the security chain's CORS filter,
 * short-circuiting error responses (e.g. 401 for a missing/expired bearer token) with no
 * {@code Access-Control-Allow-Origin} header — which the browser reports as a CORS failure even
 * though the real cause is an unauthenticated request. See {@code GatewaySecurityConfig}, which
 * constructs this filter directly (not as an injected bean) so it exists only inside the chain.
 *
 * <p>Authentication flow:
 * <ol>
 *   <li>Strip {@code X-User-Id}, {@code X-User-Role}, {@code X-Gateway-Token}, and
 *       {@code X-Internal-Secret-Key} from every incoming request — prevents clients from
 *       injecting trusted headers to impersonate users or bypass downstream checks.</li>
 *   <li>OPTIONS preflight → pass through (CORS handled by Spring Security).</li>
 *   <li>Public path → pass through (pre-compiled AntPathMatcher patterns).</li>
 *   <li>{@code X-Internal-Secret-Key} header present → validate against configured keys,
 *       grant {@code ROLE_INTERNAL_SERVICE}.</li>
 *   <li>Bearer token present → delegate to the {@link IAuthProvider} resolved by
 *       {@link AuthProviderRouter#resolveForToken}, per request, not a single fixed provider:
 *       <ul>
 *         <li>{@code HmacJwtAuthProvider} (HMAC-SHA256) is tried first regardless of
 *             {@code railcab.gateway.identity-provider.type} -- UAM mints every login mechanism
 *             (local, Google, Keycloak/OIDC-bridged) as this same token shape now, so this is
 *             what validates the overwhelming majority of real tokens correctly.</li>
 *         <li>Falls back to whichever provider {@code railcab.gateway.identity-provider.type} names
 *             ({@code keycloak}/{@code oidc}) only if the token doesn't verify as HMAC at all --
 *             i.e. a raw external-IdP token presented directly, bypassing UAM entirely.</li>
 *       </ul>
 *   </li>
 *   <li>Valid token → inject {@code X-User-Id}, {@code X-User-Role}, and {@code X-Gateway-Token}
 *       downstream. Downstream services verify {@code X-Gateway-Token} and trust the identity
 *       headers — no duplicate JWT validation needed in AdminAPI, BookingsAPI, or IRCTC.</li>
 * </ol>
 */
@Slf4j
public class ReactiveJwtAuthenticationFilter implements WebFilter, Ordered {

    private final AuthProviderRouter authProviderRouter;
    private final GatewayProperties properties;
    private final UserStatusCacheService userStatusCacheService;
    private final ObjectMapper objectMapper;
    private final Scheduler blockingOpsScheduler;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // SockJS handshake/transport requests (/info, /websocket, xhr_streaming, ...) can't carry an
    // Authorization header, so on these paths ONLY the token may arrive as ?access_token= instead
    // (Rider_Web/Rider_AdminWeb's in-app-notification-service append it). Still fully
    // authenticated + privilege-checked like any other request; NMS re-authenticates STOMP CONNECT.
    private static final List<String> QUERY_TOKEN_PATH_PATTERNS = List.of("/api/*/nms/in-app/ws/**");
    private static final String ACCESS_TOKEN_QUERY_PARAM = "access_token";

    // Pre-compiled at construction time — eliminates string allocation on every request (heuristq pattern)
    private final List<String> publicPathPatterns;
    // Public for GET only, matched exactly (PathMatchersConfig#publicGetOnlyPaths): other methods need a token.
    private final List<String> publicGetOnlyPathPatterns;

    public ReactiveJwtAuthenticationFilter(
            AuthProviderRouter authProviderRouter,
            GatewayProperties properties,
            PathMatchersConfig pathMatchersConfig,
            UserStatusCacheService userStatusCacheService,
            ObjectMapper objectMapper,
            Scheduler blockingOpsScheduler) {

        this.authProviderRouter = authProviderRouter;
        this.properties = properties;
        this.userStatusCacheService = userStatusCacheService;
        this.objectMapper = objectMapper;
        this.blockingOpsScheduler = blockingOpsScheduler;

        // Expand each raw path into exact + /** variant once, not on every request.
        // Use an array (not List.of) so null values from unset PathMatchersConfig fields are
        // filtered out safely — List.of() throws NPE on null elements.
        String[] rawPaths = {
                pathMatchersConfig.getAdminVendorPublic(),
                pathMatchersConfig.getIrctcHealth(),
                pathMatchersConfig.getIrctcSimulator(),
                pathMatchersConfig.getUamIdentityAuth(),
                pathMatchersConfig.getUamIdentityAuthVersioned(),
                pathMatchersConfig.getUamIdentityAuthVersionAfter(),
                pathMatchersConfig.getPaymentCallback(),
                pathMatchersConfig.getRiderPaymentWebhook(),
                pathMatchersConfig.getRiderWalletWebhook(),
                pathMatchersConfig.getRiderRideWebsocket(),
                pathMatchersConfig.getMatrimonyWebsocket(),
                pathMatchersConfig.getMatrimonySelfRegistrationConfig(),
                pathMatchersConfig.getMatrimonyPublicConfigOptions(),
                pathMatchersConfig.getFoodWebsocket(),
                pathMatchersConfig.getFoodPaymentWebhook(),
                pathMatchersConfig.getFoodMedia(),
                pathMatchersConfig.getFoodUploads(),
                pathMatchersConfig.getFoodHome(),
                pathMatchersConfig.getFoodCategories(),
                pathMatchersConfig.getFoodRestaurants(),
                pathMatchersConfig.getFoodItems(),
                pathMatchersConfig.getFoodSearch(),
                pathMatchersConfig.getFoodServiceability(),
                pathMatchersConfig.getRiderRideSignalingWebsocket(),
                pathMatchersConfig.getRiderOnboardingActiveSchemaPublic(),
                pathMatchersConfig.getRiderOnboardingSubmissionsPublic(),
                pathMatchersConfig.getRiderTrainSearchPublic(),
                pathMatchersConfig.getRiderTrainPublic(),
                pathMatchersConfig.getRiderCommercePublic(),
                pathMatchersConfig.getRiderConfigPublic(),
                pathMatchersConfig.getNmsCallbacks(),
                pathMatchersConfig.getActuator(),
                // Circuit-breaker internal forwards — always public regardless of profile config
                "/fallback/**",
                // OpenAPI docs / Swagger UI — this filter runs at order -100, before
                // GatewaySecurityConfig's authorizeExchange permitAll list is ever consulted, so
                // that list alone does NOT make these paths public; this filter's own
                // publicPathPatterns is the one actually deciding it here. Matched against the raw
                // incoming request path (pre-rewrite), so both the gateway's own bare paths and
                // every downstream service's docs proxied through its gateway prefix (e.g.
                // /api/payment/v3/api-docs) need covering — see RouteConfig's *-docs routes and
                // springdoc.swagger-ui.urls in application.properties.
                "/v3/api-docs", "/swagger-ui.html", "/swagger-ui/**",
                "/api/*/v3/api-docs", "/api/*/swagger-ui.html", "/api/*/swagger-ui/**",
                // Rider microservices sit one segment deeper (/api/rider/<svc>/...).
                "/api/rider/*/v3/api-docs", "/api/rider/*/swagger-ui.html", "/api/rider/*/swagger-ui/**",
                // Javadoc -- the gateway's own /javadoc index plus every service's, proxied
                // through its gateway prefix (RouteConfig's *-javadoc routes).
                "/javadoc", "/javadoc/**",
                "/api/*/javadoc", "/api/*/javadoc/**",
                "/api/rider/*/javadoc", "/api/rider/*/javadoc/**"
        };

        List<String> patterns = new ArrayList<>(rawPaths.length * 2);
        for (String path : rawPaths) {
            if (StringUtils.hasText(path)) {
                patterns.add(path);
                if (!path.endsWith("/**")) {
                    patterns.add(path + "/**");
                }
            }
        }
        this.publicPathPatterns = List.copyOf(patterns);
        this.publicGetOnlyPathPatterns = List.copyOf(pathMatchersConfig.publicGetOnlyPaths());
        log.info("Gateway public paths registered: {} (GET only: {})", this.publicPathPatterns, this.publicGetOnlyPathPatterns);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @Nonnull WebFilterChain chain) {
        ServerHttpRequest request  = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String requestUri = request.getURI().getPath();

        // Strip identity headers from every incoming request — prevents clients from forging
        // X-User-Id, X-User-Role, X-Gateway-Token, or X-Internal-Secret-Key before auth.
        // We read the internal key from the ORIGINAL request first (it will be stripped below).
        String internalKey = request.getHeaders().getFirst(GatewayConstants.INTERNAL_HEADER_NAME);
        ServerWebExchange safe = stripIdentityHeaders(exchange);

        // Preflight — CORS handled by Spring Security config
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(safe);
        }

        // Public paths bypass auth entirely
        if (isPublicPath(requestUri) || (HttpMethod.GET.equals(request.getMethod()) && isPublicGetOnlyPath(requestUri))) {
            log.debug("Public path, skipping auth: {}", requestUri);
            return chain.filter(safe);
        }

        // Internal service-to-service authentication
        if (StringUtils.hasText(internalKey)) {
            return handleInternalAuthentication(safe, chain, internalKey, requestUri);
        }

        // Bearer token authentication
        String token = extractBearerToken(request);
        if (token == null) {
            return sendError(response, HttpStatus.UNAUTHORIZED,
                    MessageConstants.UNAUTHORIZED, MessageConstants.ACCESS_TOKEN_MISSING);
        }

        // Validate token + verify account is still active — both blocking ops on blockingOpsScheduler.
        // Returns either a TokenRejection or a pre-built UsernamePasswordAuthenticationToken
        // so continueWithValidToken never needs to re-call the provider (critical for Keycloak profile
        // where each provider call is a blocking HTTP round-trip to the introspection endpoint).
        return Mono.<Object>fromCallable(() -> {
                    // IDP-agnostic: resolves HMAC first (what UAM issues for every login mechanism)
                    // regardless of railcab.gateway.identity-provider.type, falling back to the
                    // statically-configured provider only if the token isn't a valid UAM-issued JWT.
                    // The token is verified and parsed once here; everything below reuses that result.
                    TokenVerification verification = authProviderRouter.verify(token);
                    if (!verification.valid()) {
                        return verification.expired() ? TokenRejection.EXPIRED : TokenRejection.INVALID;
                    }
                    // Build auth once — reused for the DB check and passed to continueWithValidToken
                    UsernamePasswordAuthenticationToken auth = verification.authentication();
                    Object principal = auth.getPrincipal();
                    String userId = principal instanceof String s ? s : null;
                    String role = auth.getAuthorities().stream()
                            .findFirst()
                            .map(a -> a.getAuthority().replaceFirst(GatewayConstants.ROLE_PREFIX_PATTERN, ""))
                            .orElse(null);
                    if (userId != null && role != null
                            && !verification.externallyManaged()
                            && !userStatusCacheService.isActive(userId, role)) {
                        return TokenRejection.INACTIVE;
                    }
                    return auth;
                })
                .subscribeOn(blockingOpsScheduler)
                .flatMap(result -> {
                    if (result instanceof TokenRejection rejection) {
                        return switch (rejection) {
                            case EXPIRED  -> sendError(response, HttpStatus.UNAUTHORIZED,
                                    MessageConstants.UNAUTHORIZED, MessageConstants.TOKEN_EXPIRED);
                            case INVALID  -> sendError(response, HttpStatus.UNAUTHORIZED,
                                    MessageConstants.UNAUTHORIZED, MessageConstants.INVALID_TOKEN);
                            case INACTIVE -> sendError(response, HttpStatus.UNAUTHORIZED,
                                    MessageConstants.UNAUTHORIZED, MessageConstants.ACCESS_NOT_ALLOWED);
                        };
                    }
                    return continueWithValidToken(safe, chain,
                            (UsernamePasswordAuthenticationToken) result);
                })
                .onErrorResume(ex -> handleAuthError(exchange, ex));
    }

    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(GatewayConstants.X_USER_ID);
                    h.remove(GatewayConstants.X_USER_ROLE);
                    h.remove(GatewayConstants.X_USER_ROLES);
                    h.remove(GatewayConstants.X_TENANT_ID);
                    h.remove(GatewayConstants.X_ORG_WIDE);
                    h.remove(GatewayConstants.X_GATEWAY_TOKEN);
                    h.remove(GatewayConstants.X_USER_ELEVATED);
                    h.remove(GatewayConstants.INTERNAL_HEADER_NAME);
                    h.remove(GatewayConstants.X_RIDER_SERVICE_TOKEN);
                })
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private Mono<Void> continueWithValidToken(
            ServerWebExchange exchange, WebFilterChain chain,
            UsernamePasswordAuthenticationToken authentication) {
        // Kept in the exchange, never accepted from client headers or query parameters.
        exchange.getAttributes().put(CallerDetails.APPLICATIONS_ATTRIBUTE,
                authentication.getDetails() instanceof CallerDetails verified ? verified.applications() : List.of());
        // Extract identity for downstream headers from the pre-built auth token — no extra provider calls
        Object principal = authentication.getPrincipal();
        String userId = principal instanceof String s ? s : null;
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst(GatewayConstants.ROLE_PREFIX_PATTERN, ""))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String role = roles.stream().findFirst().orElse(null);
        String rolesJoined = String.join(GatewayConstants.ROLE_LIST_DELIMITER, roles);
        String gatewayToken = properties.getInternal().getGatewayForwardKey();
        String tenantId = authentication.getDetails() instanceof CallerDetails details ? details.tenantId() : null;
        boolean orgWide = authentication.getDetails() instanceof CallerDetails details && details.orgWide();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    if (StringUtils.hasText(userId))       h.set(GatewayConstants.X_USER_ID, userId);
                    if (StringUtils.hasText(tenantId))     h.set(GatewayConstants.X_TENANT_ID, tenantId);
                    if (orgWide)                           h.set(GatewayConstants.X_ORG_WIDE, "true");
                    if (StringUtils.hasText(role))         h.set(GatewayConstants.X_USER_ROLE, role);
                    if (StringUtils.hasText(rolesJoined))  h.set(GatewayConstants.X_USER_ROLES, rolesJoined);
                    if (StringUtils.hasText(gatewayToken)) h.set(GatewayConstants.X_GATEWAY_TOKEN, gatewayToken);
                })
                .build();

        return chain
                .filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Mono<Void> handleInternalAuthentication(
            ServerWebExchange exchange, WebFilterChain chain, String internalKey, String requestUri) {

        boolean valid = SecurityUtils.validateInternalTokenKey(
                internalKey, properties.getInternal().getSecretKeys());

        if (!valid) {
            log.warn("Invalid internal secret key for URI={}", requestUri);
            return sendError(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                    MessageConstants.UNAUTHORIZED, MessageConstants.INVALID_TOKEN);
        }

        log.debug("Internal service authenticated for URI={}", requestUri);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                GatewayConstants.INTERNAL_SERVICE, null,
                List.of(new SimpleGrantedAuthority(GatewayConstants.ROLE_INTERNAL_SERVICE)));

        // Inject identity headers so downstream services and PrivilegeBasedAccessFilter see the caller.
        // X-Gateway-Token proves this request was forwarded by the gateway.
        String gatewayToken = properties.getInternal().getGatewayForwardKey();
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(GatewayConstants.X_USER_ID,   GatewayConstants.INTERNAL_SERVICE)
                .header(GatewayConstants.X_USER_ROLE, GatewayConstants.INTERNAL_SERVICE_ROLE)
                .header(GatewayConstants.X_USER_ROLES, GatewayConstants.INTERNAL_SERVICE_ROLE)
                .headers(h -> {
                    if (StringUtils.hasText(gatewayToken)) h.set(GatewayConstants.X_GATEWAY_TOKEN, gatewayToken);
                })
                .build();

        return chain
                .filter(exchange.mutate().request(mutated).build())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    // For-loop with early return: allocation-free, short-circuits on first match (heuristq pattern)
    private boolean isPublicPath(String uri) {
        for (String pattern : publicPathPatterns) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicGetOnlyPath(String uri) {
        for (String pattern : publicGetOnlyPathPatterns) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(GatewayConstants.BEARER_PREFIX)) {
            return header.substring(GatewayConstants.BEARER_PREFIX.length());
        }
        String path = request.getURI().getPath();
        if (QUERY_TOKEN_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            String queryToken = request.getQueryParams().getFirst(ACCESS_TOKEN_QUERY_PARAM);
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }

    private Mono<Void> handleAuthError(ServerWebExchange exchange, Throwable throwable) {
        // This handler also sees errors raised by the route further down (for example a circuit-breaker fallback). When the response has
        // already been sent its headers are read-only, and trying to write an error body only adds a second UnsupportedOperationException.
        if (exchange.getResponse().isCommitted()) {
            log.debug("Not writing an error: the response is already committed ({})", throwable.getMessage());
            return Mono.empty();
        }
        log.error("Auth filter error: {}", throwable.getMessage());
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            statusCode = ex.getStatusCode().value();
        }
        AuthErrorMapping.ErrorDetails error = AuthErrorMapping.get(statusCode);
        return sendError(exchange.getResponse(), error.status(), error.error(), error.message());
    }

    private Mono<Void> sendError(ServerHttpResponse response, HttpStatus status, String error, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                GatewayConstants.JSON_ERROR_KEY, error,
                GatewayConstants.JSON_MESSAGE_KEY, message
        );
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
