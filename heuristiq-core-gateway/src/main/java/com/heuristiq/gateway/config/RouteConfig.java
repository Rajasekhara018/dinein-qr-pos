package com.heuristiq.gateway.config;

import com.heuristiq.gateway.filter.InternalPathGuard;

import static com.heuristiq.gateway.config.RouteHeaderConstants.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Spring Cloud Gateway route definitions.
 *
 * <p>Route strategy — gateway prefix → downstream service, resolved via Eureka service discovery
 * ({@code lb://<service-id>}, service-id = the target's {@code spring.application.name},
 * upper-cased per Eureka registry convention) by default. Set
 * {@code railcab.gateway.discovery.enabled=false} to run without a ServiceRegistry at all --
 * every route below then resolves to a static {@code railcab.gateway.static.*-url} host:port
 * instead (each defaulting to that service's own local port; see run.md), and the Eureka client
 * itself is disabled via the same flag (see application.properties) so it never attempts to
 * register with or query a registry that isn't part of this deployment:
 * <pre>
 *  /api/admin/**    → RAILCAB-ADMIN-API
 *                     Rewrites /api/admin/{path} → /api/{path}
 *
 *  /api/bookings/** → RAILCAB-BOOKINGSAPI
 *                     Rewrites /api/bookings/{path} → /api/v1/{path}
 *
 *  /api/irctc/**    → RAILCAB-IRCTC-API-INTEGRATION
 *                     Rewrites /api/irctc/{path} → /api/v1/{path}, except health/stats/cache/
 *                     reconciliation-admin paths (route "irctc-health"), which stay unversioned
 *                     and rewrite to /{path} as before.
 *
 *  {@code /api/v1/<service>/**} → the rider microservices (ride, customer, dispatch, payment, onboarding,
 *                     workflow, location, config, train, pricing, audit, wallet, accounting). Each service serves exactly
 *                     the client-facing path, so these routes forward the request UNCHANGED (no rewrite).
 *                     Static-URL routing unless riderServicesDiscoveryEnabled -- see below.
 * </pre>
 *
 * <p>Each route includes:
 * <ul>
 *   <li>Circuit breaker (Resilience4j) with a service-specific fallback URI.</li>
 *   <li>Redis rate limiting — applied when {@code railcab.gateway.rate-limit.enabled=true}.</li>
 *   <li>{@code X-Forwarded-By} header for downstream audit.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RouteConfig {

    // Service discovery (Eureka/ServiceRegistry) toggle -- when false, every route below resolves
    // to a static host:port instead of lb://<service-id>, and eureka.client.enabled is tied to
    // this same flag (see application.properties) so the gateway doesn't even attempt to register
    // with or query a ServiceRegistry that isn't part of this deployment. Defaults to true,
    // preserving today's Eureka-based routing.
    @Value("${railcab.gateway.discovery.enabled:true}")
    private boolean discoveryEnabled;

    // Defaults corrected against this repo's actual services: Rider_AdminAPI runs on 9005
    // (server.port in Rider_AdminAPI/src/main/resources/application.properties) and
    // Rider_BookingsAPI on 9006 (same file, Rider_BookingsAPI) -- NOT the 9000/9001 the upstream
    // RailCab gateway assumed, which belong to a different deployment's port allocation.
    @Value("${railcab.gateway.static.admin-api-url:http://localhost:9005}")
    private String adminApiStaticUrl;

    @Value("${railcab.gateway.static.bookings-api-url:http://localhost:9006}")
    private String bookingsApiStaticUrl;

    // Rider_ServiceRegistry isn't registered with itself in Eureka, so it's always static.
    @Value("${railcab.gateway.static.registry-url:http://localhost:9100}")
    private String registryStaticUrl;

    // NOTE: irctc/uam/payment-api/search-api below belong to the broader RailCab enterprise
    // platform this gateway was copied from — none of these exist in bestride-rider-app (dms and
    // nms DO exist now, as Rider_DMS/Rider_NMS, on their corrected 9104/9111 ports below). The
    // remaining irctc/uam/payment-api/search-api routes are left in place (harmless if unused:
    // with discoveryEnabled false and nothing listening on these ports, requests just
    // fail/circuit-break) rather than deleted, but do not point railcab.gateway.static.payment-api-url
    // at 9006 in this repo -- that collides with the corrected bookings-api-url port above.
    // (uam-api-url's default of 9103 is correct and matches Rider_UAM's real port.)
    @Value("${railcab.gateway.static.irctc-api-url:http://localhost:9002}")
    private String irctcApiStaticUrl;

    @Value("${railcab.gateway.static.uam-api-url:http://localhost:9103}")
    private String uamApiStaticUrl;

    @Value("${railcab.gateway.static.dms-api-url:http://localhost:9104}")
    private String dmsApiStaticUrl;

    // Rider_NMS runs on 9111 in this repo (not the upstream 9005 default, which collides with
    // Rider_AdminAPI's real port here) -- see Rider_NMS/src/main/resources/application.properties.
    @Value("${railcab.gateway.static.nms-api-url:http://localhost:9111}")
    private String nmsApiStaticUrl;

    @Value("${railcab.gateway.static.search-api-url:http://localhost:9116}")
    private String searchApiStaticUrl;

    private String adminApiUri() {
        return discoveryEnabled ? "lb://REALBOOM-ADMIN-API" : adminApiStaticUrl;
    }

    // Rider_BookingsAPI has no Eureka client dependency, so it never registers -- an lb:// id
    // here always resolved to "no instances" (503 Service Unavailable). Always static.
    private String bookingsApiUri() {
        return bookingsApiStaticUrl;
    }

    private String irctcApiUri() {
        return discoveryEnabled ? "lb://RAILCAB-IRCTC-API-INTEGRATION" : irctcApiStaticUrl;
    }

    private String uamApiUri() {
        return discoveryEnabled ? "lb://REALBOOM-UAM" : uamApiStaticUrl;
    }

    private String dmsApiUri() {
        return discoveryEnabled ? "lb://REALBOOM-DMS" : dmsApiStaticUrl;
    }

    private String nmsApiUri() {
        return discoveryEnabled ? "lb://REALBOOM-NMS" : nmsApiStaticUrl;
    }

    private String nmsWebSocketUri() {
        String uri = nmsApiUri();
        return uri.startsWith(LB_SCHEME) ? LB_WS_SCHEME + uri.substring(LB_SCHEME.length())
                : uri.replaceFirst("^http", "ws");
    }

    private String searchApiUri() {
        return discoveryEnabled ? "lb://REALBOOM-SEARCH-API" : searchApiStaticUrl;
    }

    // ── Rider microservices (extracted from Rider_BookingsAPI per the Rider App MVP HLD's
    // Option B — Ride | Driver | Pricing | Payment | Location) ─────────────────────────────────
    // None of these register with Eureka yet (no eureka-client dependency in any of the 5 new
    // services), so they get their own discovery toggle instead of sharing the platform-wide
    // railcab.gateway.discovery.enabled flag above -- flipping that flag on must not silently
    // break these routes by pointing them at an lb:// service-id nothing has ever registered.
    // Flip railcab.gateway.rider-services.discovery-enabled=true once each service actually adds
    // spring-cloud-starter-netflix-eureka-client and registers under the RIDER-* ids below.
    @Value("${railcab.gateway.rider-services.discovery-enabled:false}")
    private boolean riderServicesDiscoveryEnabled;

    @Value("${railcab.gateway.static.rider-pricing-api-url:}")
    private String riderPricingApiStaticUrl;

    @Value("${railcab.gateway.static.rider-location-api-url:}")
    private String riderLocationApiStaticUrl;

    @Value("${railcab.gateway.static.rider-dispatch-api-url:}")
    private String riderDispatchApiStaticUrl;

    @Value("${railcab.gateway.static.rider-ride-api-url:}")
    private String riderRideApiStaticUrl;

    @Value("${railcab.gateway.static.rider-payment-api-url:}")
    private String riderPaymentApiStaticUrl;

    // Rider_CustomerService — a deliberate consolidation of three small, low-traffic domains
    // (Customer profile, Rating/feedback, Support tickets) into ONE service rather than three,
    // per the same over-engineering-avoidance reasoning as the rest of this section. Not part of
    // Option B's original 5-box diagram; added afterward once these gaps were identified.
    @Value("${railcab.gateway.static.rider-customer-api-url:}")
    private String riderCustomerApiStaticUrl;

    // Rider_WorkflowService — generic Flowable-backed review/approval engine, a platform
    // capability like DMS/NMS rather than a domain owner. Rider_OnboardingService is its first
    // consumer; nothing calls this route through the gateway yet (Onboarding calls it
    // service-to-service), but it's routed here so future consumers can reach it the same way.
    @Value("${railcab.gateway.static.rider-workflow-api-url:}")
    private String riderWorkflowApiStaticUrl;

    @Value("${railcab.gateway.static.rider-onboarding-api-url:}")
    private String riderOnboardingApiStaticUrl;

    // matrimonial-api does not register with Eureka, so this URL is the way to reach it.
    @Value("${railcab.gateway.static.matrimony-api-url:}")
    private String matrimonyApiStaticUrl;

    @Value("${railcab.gateway.static.food-api-url:}")
    private String foodApiStaticUrl;

    // Rider_CommerceService -- the Best Purchase shop: catalogue, carts and customer orders.
    @Value("${railcab.gateway.static.rider-commerce-api-url:}")
    private String riderCommerceApiStaticUrl;

    // Rider_TrainService -- IRCTC train search, booking, cancellation/TDR, refunds and admin APIs.
    @Value("${railcab.gateway.static.rider-train-api-url:}")
    private String riderTrainApiStaticUrl;

    // Rider_ConfigService -- admin-managed settings, cities, banners, FAQs and app versions.
    @Value("${railcab.gateway.static.rider-config-api-url:}")
    private String riderConfigApiStaticUrl;

    // Rider_AuditService -- central audit trail: query API for AdminWeb and screen-view intake for the apps.
    @Value("${railcab.gateway.static.rider-audit-api-url:}")
    private String riderAuditApiStaticUrl;

    // Rider_ReportService -- report configuration, generation, downloads and the reports audit trail (all POST).
    @Value("${railcab.gateway.static.rider-reports-api-url:}")
    private String riderReportsApiStaticUrl;

    // Rider_WalletService -- shared credits ledger (customer wallet, agent float and earnings, per-app ledgers).
    @Value("${railcab.gateway.static.rider-wallet-api-url:}")
    private String riderWalletApiStaticUrl;

    // Rider_AccountingService -- the company's general ledger (chart of accounts, trial balance), built from Wallet's journals.
    @Value("${railcab.gateway.static.rider-accounting-api-url:}")
    private String riderAccountingApiStaticUrl;

    // Rider_FranchiseService -- franchises, plans, per-application terms and agents (moved out of Rider_UAM).
    @Value("${railcab.gateway.static.rider-franchise-api-url:}")
    private String riderFranchiseApiStaticUrl;

    // Rider_DriverEarningsService -- only its Swagger docs are routed through the gateway so far.
    @Value("${railcab.gateway.static.rider-driver-earnings-api-url:}")
    private String riderDriverEarningsApiStaticUrl;

    private String riderDriverEarningsApiUri() {
        return riderUri("REALBOOM-DRIVEREARNINGSSERVICE", riderDriverEarningsApiStaticUrl, 9109);
    }

    private String riderCustomerApiUri() {
        return riderUri("REALBOOM-CUSTOMERSERVICE", riderCustomerApiStaticUrl, 9107);
    }

    private String riderWorkflowApiUri() {
        return riderUri("REALBOOM-WORKFLOWSERVICE", riderWorkflowApiStaticUrl, 9119);
    }

    private String riderTrainApiUri() {
        return riderUri("REALBOOM-TRAINSERVICE", riderTrainApiStaticUrl, 9118);
    }

    private String riderCommerceApiUri() {
        return riderUri("REALBOOM-COMMERCESERVICE", riderCommerceApiStaticUrl, 9127);
    }

    private String riderConfigApiUri() {
        return riderUri("REALBOOM-CONFIGSERVICE", riderConfigApiStaticUrl, 9102);
    }

    private String riderAuditApiUri() {
        return riderUri("REALBOOM-AUDITSERVICE", riderAuditApiStaticUrl, 9106);
    }

    private String riderReportsApiUri() {
        return riderUri("REALBOOM-REPORT-SERVICE", riderReportsApiStaticUrl, 9122);
    }

    private String riderWalletApiUri() {
        return riderUri(WalletRouteConstants.SERVICE_ID, riderWalletApiStaticUrl, WalletRouteConstants.LOCAL_PORT);
    }

    private String riderAccountingApiUri() {
        return riderUri(AccountingRouteConstants.SERVICE_ID, riderAccountingApiStaticUrl, AccountingRouteConstants.LOCAL_PORT);
    }

    private String riderFranchiseApiUri() {
        return riderUri(FranchiseRouteConstants.SERVICE_ID, riderFranchiseApiStaticUrl, FranchiseRouteConstants.LOCAL_PORT);
    }

    /** The food backend is not on the service registry: its static URL, or its default local port. */
    private String foodApiUri() {
        return StringUtils.hasText(foodApiStaticUrl) ? foodApiStaticUrl : FoodRouteConstants.LOCAL_URL;
    }

    /** The static URL always wins: matrimonial-api is not on the service registry, even when Rider-service discovery is forced on. */
    private String matrimonyApiUri() {
        return StringUtils.hasText(matrimonyApiStaticUrl)
                ? matrimonyApiStaticUrl
                : riderUri("MATRIMONIAL-API", matrimonyApiStaticUrl, 9120);
    }

    private String matrimonyWebSocketUri() {
        String uri = matrimonyApiUri();
        return uri.startsWith(LB_SCHEME) ? LB_WS_SCHEME + uri.substring(LB_SCHEME.length())
                : uri.replaceFirst("^http", "ws");
    }

    private String foodWebSocketUri() {
        String uri = foodApiUri();
        return uri.startsWith(LB_SCHEME) ? LB_WS_SCHEME + uri.substring(LB_SCHEME.length())
                : uri.replaceFirst("^http", "ws");
    }

    private String riderOnboardingApiUri() {
        return riderUri("REALBOOM-ONBOARDINGSERVICE", riderOnboardingApiStaticUrl, 9112);
    }

    private String riderPricingApiUri() {
        return riderUri("REALBOOM-PRICINGSERVICE", riderPricingApiStaticUrl, 9114);
    }

    private String riderLocationApiUri() {
        return riderUri("REALBOOM-LOCATIONSERVICE", riderLocationApiStaticUrl, 9110);
    }

    private String riderDispatchApiUri() {
        return riderUri("REALBOOM-DISPATCH-SERVICE", riderDispatchApiStaticUrl, 9108);
    }

    private String riderRideApiUri() {
        return riderUri("REALBOOM-RIDESERVICE", riderRideApiStaticUrl, 9115);
    }

    private String riderRideWebSocketUri() {
        String uri = riderRideApiUri();
        return uri.startsWith(LB_SCHEME) ? LB_WS_SCHEME + uri.substring(LB_SCHEME.length())
                : uri.replaceFirst("^http", "ws");
    }

    /**
     * A rider service's base URI: Eureka when rider-services discovery is forced on; otherwise its
     * static URL when one is configured (RIDER_*_SERVICE_URL); otherwise Eureka again when the
     * gateway's discovery is enabled -- every rider service registers under {@code serviceId} --
     * so a service with no URL configured no longer defaults to localhost (the gateway's own
     * container), which is what made the Workflow/Onboarding/Driver Earnings routes 500.
     */
    private String riderUri(String serviceId, String staticUrl, int localPort) {
        if (riderServicesDiscoveryEnabled) {
            return LB_SCHEME + serviceId;
        }
        if (StringUtils.hasText(staticUrl)) {
            return staticUrl;
        }
        return discoveryEnabled ? LB_SCHEME + serviceId : LOCAL_HTTP_PREFIX + localPort;
    }

    private String riderPaymentApiUri() {
        return riderUri("REALBOOM-PAYMENTSERVICE", riderPaymentApiStaticUrl, 9113);
    }

    @Value("${railcab.gateway.internal.nms-api-key:}")
    private String nmsApiKey;

    // mTLS proof-of-concept (see docs/design/Platform/SecretsManagement.md): when enabled, calls
    // PaymentAPI directly over HTTPS instead of through Eureka's lb:// load-balancing, since
    // wiring Spring Cloud LoadBalancer to prefer HTTPS for just one registered service (via
    // Eureka's secure-port advertisement) is real follow-up work, not done in this proof-of-concept
    // pass. Every other route is completely unaffected either way.
    @Value("${railcab.gateway.mtls.enabled:false}")
    private boolean mtlsEnabled;

    @Value("${railcab.gateway.mtls.payment-api-url:https://localhost:9006}")
    private String paymentApiMtlsUrl;

    @Value("${railcab.gateway.static.payment-api-url:http://localhost:9006}")
    private String paymentApiStaticUrl;

    private String paymentApiUri() {
        if (mtlsEnabled) {
            return paymentApiMtlsUrl;
        }
        return discoveryEnabled ? "lb://REALBOOM-PAYMENT-API" : paymentApiStaticUrl;
    }

    // Injected only when rate limiting is enabled (ConditionalOnProperty in RateLimiterConfig)
    @Autowired(required = false)
    @Qualifier("apiRateLimiter")
    private RedisRateLimiter apiRateLimiter;

    // Stricter limiter (5 req/s vs 50 req/s) for the auth surface (login/register/OTP) --
    // the most abuse-prone endpoints on the platform. See uam-auth-api route below.
    @Autowired(required = false)
    @Qualifier("authRateLimiter")
    private RedisRateLimiter authRateLimiter;

    @Autowired(required = false)
    @Qualifier("userOrIpKeyResolver")
    private KeyResolver userOrIpKeyResolver;

    // Auth surface: keyed by the real client address, in a bucket of its own (see RateLimitConstants).
    @Autowired(required = false)
    @Qualifier("authIpKeyResolver")
    private KeyResolver authIpKeyResolver;

    // Password login only (uam-login-api route): a limit per client address AND one shared limit for all callers, sized to
    // what UAM can hash per second, so an overload is refused here with a 429 instead of timing out in UAM's bcrypt queue (503).
    @Autowired(required = false)
    @Qualifier("loginRateLimiter")
    private RedisRateLimiter loginRateLimiter;

    @Autowired(required = false)
    @Qualifier("loginGlobalRateLimiter")
    private RedisRateLimiter loginGlobalRateLimiter;

    @Autowired(required = false)
    @Qualifier("loginIpKeyResolver")
    private KeyResolver loginIpKeyResolver;

    @Autowired(required = false)
    @Qualifier("loginGlobalKeyResolver")
    private KeyResolver loginGlobalKeyResolver;

    // Public live-trip tracking only (rider-ride-public-track route): the caller is never signed in, so its own limit per client
    // address, applied before the circuit breaker like the auth surface.
    @Autowired(required = false)
    @Qualifier("publicTrackRateLimiter")
    private RedisRateLimiter publicTrackRateLimiter;

    @Autowired(required = false)
    @Qualifier("publicTrackIpKeyResolver")
    private KeyResolver publicTrackIpKeyResolver;

    // Token refresh only (uam-refresh-api route): its own, more generous limit per client address, because many phones can share
    // one carrier NAT address and a refused refresh logs the user out.
    @Autowired(required = false)
    @Qualifier("refreshRateLimiter")
    private RedisRateLimiter refreshRateLimiter;

    @Autowired(required = false)
    @Qualifier("refreshIpKeyResolver")
    private KeyResolver refreshIpKeyResolver;

    // Public sign-up only (uam-register-api route): an hourly limit per client address on top of the auth surface's limit,
    // because sign-up answers 409 for a phone or email that already has an account.
    @Autowired(required = false)
    @Qualifier("registerRateLimiter")
    private RedisRateLimiter registerRateLimiter;

    @Autowired(required = false)
    @Qualifier("registerIpKeyResolver")
    private KeyResolver registerIpKeyResolver;

    private final GatewayProperties properties;

    public RouteConfig(GatewayProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RouteLocator railCabRoutes(RouteLocatorBuilder builder) {
        log.info("Gateway routes: admin={}, bookings={}, irctc={}, uam={}, dms={}, pay={}, nms={}, search={}, "
                        + "riderPricing={}, riderLocation={}, riderDispatch={}, riderRide={}, riderPayment={}, "
                        + "riderCustomer={}, riderWorkflow={}, riderOnboarding={}, rateLimitEnabled={}",
                adminApiUri(), bookingsApiUri(), irctcApiUri(), uamApiUri(), dmsApiUri(), paymentApiUri(), nmsApiUri(),
                searchApiUri(), riderPricingApiUri(), riderLocationApiUri(), riderDispatchApiUri(), riderRideApiUri(),
                riderPaymentApiUri(), riderCustomerApiUri(), riderWorkflowApiUri(), riderOnboardingApiUri(),
                properties.getRateLimit().isEnabled());

        boolean rateLimitEnabled = properties.getRateLimit().isEnabled()
                && apiRateLimiter != null && userOrIpKeyResolver != null;
        boolean authRateLimitEnabled = properties.getRateLimit().isEnabled()
                && authRateLimiter != null && authIpKeyResolver != null;
        boolean loginRateLimitEnabled = properties.getRateLimit().isEnabled()
                && loginRateLimiter != null && loginGlobalRateLimiter != null
                && loginIpKeyResolver != null && loginGlobalKeyResolver != null;
        boolean publicTrackRateLimitEnabled = properties.getRateLimit().isEnabled()
                && publicTrackRateLimiter != null && publicTrackIpKeyResolver != null;
        boolean refreshRateLimitEnabled = properties.getRateLimit().isEnabled()
                && refreshRateLimiter != null && refreshIpKeyResolver != null;
        boolean registerRateLimitEnabled = properties.getRateLimit().isEnabled()
                && registerRateLimiter != null && registerIpKeyResolver != null;

        return builder.routes()

                // ── Javadoc (public) ─────────────────────────────────────────
                // Every service ships its javadoc inside its jar as static content at
                // /javadoc/**; /api/<service>/javadoc/<page> maps to it (bare /javadoc/ opens
                // index.html). The gateway's own /javadoc index page lists them all.
                .route("admin-javadoc", r -> javadocRoute(r, "/api/admin", adminApiUri()))
                .route("bookings-javadoc", r -> javadocRoute(r, "/api/bookings", bookingsApiUri()))
                .route("uam-javadoc", r -> javadocRoute(r, "/api/uam", uamApiUri()))
                .route("dms-javadoc", r -> javadocRoute(r, "/api/dms", dmsApiUri()))
                .route("nms-javadoc", r -> javadocRoute(r, "/api/nms", nmsApiUri()))
                .route("search-javadoc", r -> javadocRoute(r, "/api/search", searchApiUri()))
                .route("registry-javadoc", r -> javadocRoute(r, "/api/registry", registryStaticUrl))
                .route("rider-pricing-javadoc", r -> javadocRoute(r, "/api/rider/pricing", riderPricingApiUri()))
                .route("rider-location-javadoc", r -> javadocRoute(r, "/api/rider/location", riderLocationApiUri()))
                .route("rider-dispatch-javadoc", r -> javadocRoute(r, "/api/rider/dispatch", riderDispatchApiUri()))
                .route("rider-ride-javadoc", r -> javadocRoute(r, "/api/rider/ride", riderRideApiUri()))
                .route("rider-payment-javadoc", r -> javadocRoute(r, "/api/rider/payment", riderPaymentApiUri()))
                .route("rider-customer-javadoc", r -> javadocRoute(r, "/api/rider/customer", riderCustomerApiUri()))
                .route("rider-workflow-javadoc", r -> javadocRoute(r, "/api/rider/workflow", riderWorkflowApiUri()))
                .route("rider-onboarding-javadoc", r -> javadocRoute(r, "/api/rider/onboarding", riderOnboardingApiUri()))
                .route("rider-train-javadoc", r -> javadocRoute(r, "/api/rider/train", riderTrainApiUri()))
                .route("rider-commerce-javadoc", r -> javadocRoute(r, "/api/rider/commerce", riderCommerceApiUri()))
                .route("rider-config-javadoc", r -> javadocRoute(r, "/api/rider/config", riderConfigApiUri()))
                .route("rider-audit-javadoc", r -> javadocRoute(r, "/api/rider/audit", riderAuditApiUri()))
                .route(WalletRouteConstants.JAVADOC_ROUTE_ID, r -> javadocRoute(r, WalletRouteConstants.DOCS_PREFIX, riderWalletApiUri()))
                .route(AccountingRouteConstants.JAVADOC_ROUTE_ID, r -> javadocRoute(r, AccountingRouteConstants.DOCS_PREFIX, riderAccountingApiUri()))
                .route(FranchiseRouteConstants.JAVADOC_ROUTE_ID, r -> javadocRoute(r, FranchiseRouteConstants.DOCS_PREFIX, riderFranchiseApiUri()))
                .route("rider-driver-earnings-javadoc", r -> javadocRoute(r, "/api/rider/driver-earnings", riderDriverEarningsApiUri()))

                // ── BookingsAPI — Swagger/OpenAPI docs ───────────────────────
                // BookingsAPI now versions via ApiVersionConfig (per-@RestController path prefix)
                // instead of server.servlet.context-path, so docs (registered outside Spring MVC's
                // request-mapping layer) live at bare /v3/api-docs again, same as pay/nms/search/dms.
                // Declared before bookings-api so it wins for these specific paths.
                .route("bookings-docs", r -> r
                        .path("/api/bookings/v3/api-docs/**", "/api/bookings/swagger-ui/**", "/api/bookings/swagger-ui.html")
                        .filters(f -> f
                                .rewritePath("/api/bookings/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/bookings"))
                        .uri(bookingsApiUri()))

                // ── BookingsAPI — Actuator health ─────────────────────────────
                // Same reasoning as bookings-docs: actuator isn't a @RestController, so it isn't
                // touched by ApiVersionConfig's /api/v1 prefix and now lives at bare /actuator
                // (previously /api/v1/actuator under the old context-path). Without this route,
                // pathmatchers.bookings-health's bypass in GatewaySecurityConfig/
                // PrivilegeBasedAccessFilter would still permit the request through auth, but the
                // bookings-api route below would then rewrite it to a nonexistent
                // /api/v1/actuator/** on the backend. Declared before bookings-api so it wins.
                .route("bookings-health", r -> r.path("/api/bookings/actuator/**")
                        .filters(f -> f
                                .rewritePath("/api/bookings/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE))
                        .uri(bookingsApiUri()))

                // ── BookingsAPI ─────────────────────────────────────────────
                // Accepts the version either after the service segment (/api/bookings/v{N}/...,
                // the original no-new-routes-needed shape) or before it (/api/v{N}/bookings/...,
                // added on request) -- RequestVersioning.resolve() decomposes either into the same
                // (service, backendPath).
                .route("bookings-api", r -> r.path("/api/bookings/**", "/api/*/bookings/**")
                        .filters(f -> standardRoute(f, "/api/bookings",
                                "bookings-cb", "bookings", rateLimitEnabled, apiRateLimiter))
                        .uri(bookingsApiUri()))

                // ── IRCTC Integration — health/ops endpoints, unversioned ────────
                // IrctcSimulatorController/BookingController/etc. moved to /api/v1, but
                // HealthController (health/stats/cache/reconciliation-admin) intentionally stayed
                // unversioned -- infra monitoring endpoints, not business API surface. Declared
                // before the general irctc-api route below so it wins for these specific paths.
                .route("irctc-health", r -> r
                        .path("/api/irctc/health/**", "/api/irctc/api/**", "/api/irctc/admin/reconciliation/**")
                        .filters(f -> f
                                .rewritePath("/api/irctc/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE))
                        .uri(irctcApiUri()))

                // ── IRCTC Integration ────────────────────────────────────────
                .route("irctc-api", r -> r.path("/api/irctc/**", "/api/*/irctc/**")
                        .filters(f -> standardRoute(f, "/api/irctc",
                                "irctc-cb", "irctc", rateLimitEnabled, apiRateLimiter))
                        .uri(irctcApiUri()))

                // ── UAM — Swagger/OpenAPI docs ───────────────────────────────
                // UAM versions per-controller (no context-path), so docs live at bare /v3/api-docs;
                // uam-api's rewrite would send /api/uam/v3/api-docs to /api/v1/uam/v3/api-docs.
                .route("uam-docs", r -> docsRoute(r, "/api/uam", uamApiUri()))

                // ── UAM — auth surface (login/register/OTP/refresh/reset-password) ──
                // Gets the stricter authRateLimiter (5 req/s, burst 10) instead of the general 50 req/s limiter --
                // the most brute-force/abuse-prone surface on the platform. Declared before uam-api so this more
                // specific pattern wins. UAM serves /api/v1/uam/** itself, so nothing is rewritten.
                // Password login (POST) is declared first and gets two more limits on top of the abuse protection of the
                // surface: a per-address limit and one limit for all callers together (see loginRoute).
                .route(RateLimitConstants.LOGIN_ROUTE_ID, r -> r.path(RateLimitConstants.LOGIN_PATH)
                        .and().method(HttpMethod.POST)
                        .filters(f -> loginRoute(f, loginRateLimitEnabled, authRateLimitEnabled))
                        .uri(uamApiUri()))

                // Token refresh (POST) gets its own, more generous limiter instead of the auth surface's 5 req/s; when it is not
                // available the route keeps the auth surface's limit, so refresh is never less protected than before.
                .route(RateLimitConstants.REFRESH_ROUTE_ID, r -> r.path(RateLimitConstants.REFRESH_PATH)
                        .and().method(HttpMethod.POST)
                        .filters(f -> refreshRateLimitEnabled
                                ? authRoute(f, "/api/v1/uam", "uam-cb", "uam", true, refreshRateLimiter, refreshIpKeyResolver)
                                : authRoute(f, "/api/v1/uam", "uam-cb", "uam", authRateLimitEnabled, authRateLimiter,
                                        authIpKeyResolver))
                        .uri(uamApiUri()))

                // Public sign-up (POST): the auth surface's limit plus an hourly budget per client address, so the 409 it gives
                // for an existing phone or email cannot be used to check a list of numbers (see registerRoute).
                .route(RateLimitConstants.REGISTER_ROUTE_ID, r -> r.path(RateLimitConstants.REGISTER_PATH)
                        .and().method(HttpMethod.POST)
                        .filters(f -> registerRoute(f, registerRateLimitEnabled, authRateLimitEnabled))
                        .uri(uamApiUri()))

                .route("uam-auth-api", r -> r.path("/api/v1/uam/identity/auth/**")
                        .filters(f -> authRoute(f, "/api/v1/uam",
                                "uam-cb", "uam", authRateLimitEnabled, authRateLimiter, authIpKeyResolver))
                        .uri(uamApiUri()))

                // /api/v1/uam/internal/** (service-token only, called service to service by base URL) and the actuator
                // are refused here with 404: they are never reachable from outside the platform.
                .route("uam-api", r -> r.path("/api/v1/uam/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(UamRouteConstants.INTERNAL_PATH));
                            f.filter(InternalPathGuard.blockInternalPaths(UamRouteConstants.ACTUATOR_PATH));
                            return directRoute(f, "/api/v1/uam",
                                    "uam-cb", "uam", rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(uamApiUri()))

                // ── DMS — Swagger/OpenAPI docs ───────────────────────────────
                // DMS now versions via ApiVersionConfig instead of server.servlet.context-path, so
                // docs live at bare /v3/api-docs again. Declared before dms-api so it wins for
                // these specific paths.
                .route("dms-docs", r -> r
                        .path("/api/dms/v3/api-docs/**", "/api/dms/swagger-ui/**", "/api/dms/swagger-ui.html")
                        .filters(f -> f
                                .rewritePath("/api/dms/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/dms"))
                        .uri(dmsApiUri()))

                // /api/v1/dms/internal/** (service-token only, called by Rider_WorkflowService) is refused here with
                // 404: it is never reachable from outside the platform.
                .route("dms-api", r -> r.path("/api/v1/dms/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(DmsRouteConstants.INTERNAL_PATH));
                            return directRoute(f, "/api/v1/dms",
                                    "dms-cb", "dms", rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(dmsApiUri()))

                // "{version:v\d+}", not "*": "*" also matched "rider", so /api/rider/payment/** (incl.
                // its /v3/api-docs) was sent to the legacy payment API and its 503 fallback.
                // Rider_PaymentService at /api/v1/payment/... (also the Razorpay webhooks, public by config - see
                // pathmatchers.rider-payment-webhook). The version-less /api/payment/... below stays on the
                // legacy payment gateway. Must precede pay-api.
                .route("rider-payment-api", r -> r.path("/api/v1/payment/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/payment",
                                "rider-payment-cb", "rider-payment", rateLimitEnabled, apiRateLimiter))
                        .uri(riderPaymentApiUri()))

                .route("pay-api", r -> r.path("/api/payment/**", "/api/{version:v\\d+}/payment/**")
                        .filters(f -> standardRoute(f, "/api/payment",
                                "pay-cb", "payment", rateLimitEnabled, apiRateLimiter))
                        .uri(paymentApiUri()))

                // ── NMS — Swagger/OpenAPI docs ───────────────────────────────
                // NMS has no context-path and the business route below does no rewrite at all
                // (dual-mapped controllers) -- but docs still live at bare /v3/api-docs, not under
                // /api/nms/, so they need their own prefix-stripping route too.
                .route("nms-docs", r -> r
                        .path("/api/nms/v3/api-docs/**", "/api/nms/swagger-ui/**", "/api/nms/swagger-ui.html")
                        .filters(f -> f
                                .rewritePath("/api/nms/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/nms"))
                        .uri(nmsApiUri()))

                // ── NMS (legacy) ─────────────────────────────────────────────
                // Predates RequestVersioning -- the client-facing path already contains "v1"
                // literally and is forwarded completely unchanged. Left exactly as-is for existing
                // callers; declared before nms-api-versioned below so it wins for that path even
                // though nms-api-versioned's own "/api/*/nms/**" pattern would also match it --
                // route order (first-match-wins) keeps the legacy no-rewrite behavior exact.
                // SockJS/STOMP in-app notification channel -- needs its own ws://-scheme route, same
                // reasoning as rider-ride-websocket above: a plain http/lb-scheme route only ever runs
                // through NettyRoutingFilter, which proxies regular HTTP and can't perform the protocol
                // upgrade handshake a WebSocket transport needs, so the upgrade request comes back as a
                // 400 instead of 101 Switching Protocols. Declared before nms-api so it wins for this
                // sub-path; forwarded unchanged (nms-api itself does no rewrite either).
                // Matched narrowly to the literal "/websocket" leaf: SockJS multiplexes several
                // sub-transports under this same "/ws/<server>/<session>/..." prefix (info, xhr,
                // xhr_streaming, etc.), and only "/websocket" is an actual protocol-upgrade request --
                // routing those plain-HTTP fallback transports through a ws://-scheme route forces them
                // through the WebSocket routing filter too, which can't serve them and 404s.
                // Two single-segment "*" wildcards (not "**"): Spring's PathPatternParser rejects
                // "**" anywhere but the start/end of a pattern, and SockJS's own path shape here is
                // always exactly "/<server-id>/<session-id>/websocket" anyway.
                .route("nms-in-app-websocket", r -> r.path("/api/v1/nms/in-app/ws/*/*/websocket")
                        .filters(f -> {
                            if (StringUtils.hasText(nmsApiKey)) {
                                f.addRequestHeader(NMS_API_KEY_HEADER, nmsApiKey);
                            }
                            return f;
                        })
                        .uri(nmsWebSocketUri()))

                // NMS's /api/v1/nms/internal/** (service-token only) is refused here with 404: the gateway adds its NMS API
                // key to every forwarded request, so that key proves nothing about the caller.
                .route("nms-api", r -> r.path("/api/v1/nms/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(InternalRouteConstants.SERVICE_INTERNAL_PATH));
                            applyResilience(f, "nms-cb", "nms", "/api/nms");
                            if (StringUtils.hasText(nmsApiKey)) {
                                f.addRequestHeader(NMS_API_KEY_HEADER, nmsApiKey);
                            }
                            applyRateLimiterIfEnabled(f, rateLimitEnabled, apiRateLimiter);
                            return f;
                        })
                        .uri(nmsApiUri()))

                // ── NMS (versioned) ──────────────────────────────────────────
                // Brings NMS onto the same /api/<service>/v{N}/... (and /api/v{N}/<service>/...)
                // scheme every other service uses (see RequestVersioning, whose table carries an
                // "nms" bare-prefix entry that also correctly decomposes the legacy "/api/v1/nms"
                // shape) -- an unversioned or explicit v1 call here projects to the exact same
                // backend path as the legacy route above, and an explicit v2 call is ready for
                // whenever NMS grows a v2 controller. Declared after nms-docs so that route still
                // wins for /api/nms/v3/api-docs/**, etc.
                .route("nms-api-versioned", r -> r.path("/api/nms/**", "/api/*/nms/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(InternalRouteConstants.SERVICE_INTERNAL_PATH));
                            f.filter(versionAwareRewrite("/api/nms"));
                            applyResilience(f, "nms-cb", "nms", "/api/nms");
                            if (StringUtils.hasText(nmsApiKey)) {
                                f.addRequestHeader(NMS_API_KEY_HEADER, nmsApiKey);
                            }
                            applyRateLimiterIfEnabled(f, rateLimitEnabled, apiRateLimiter);
                            return f;
                        })
                        .uri(nmsApiUri()))

                // ── AdminAPI — Swagger/OpenAPI docs ──────────────────────────
                // AdminAPI now versions via ApiVersionConfig instead of server.servlet.context-path,
                // so docs live at bare /v3/api-docs again. Declared before admin-api so it wins for
                // these specific paths.
                .route("admin-docs", r -> r
                        .path("/api/admin/v3/api-docs/**", "/api/admin/swagger-ui/**", "/api/admin/swagger-ui.html")
                        .filters(f -> f
                                .rewritePath("/api/admin/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/admin"))
                        .uri(adminApiUri()))

                // AdminAPI serves /api/v1/admin/** itself (incl. the public vendor registration and payment
                // webhooks), so nothing is rewritten.
                .route("admin-api", r -> r.path("/api/v1/admin/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/admin",
                                "admin-cb", "admin", rateLimitEnabled, apiRateLimiter))
                        .uri(adminApiUri()))

                // Customer and driver help desk (support tickets from the apps). Rider_AdminAPI owns the tickets and serves
                // exactly /api/v1/helpdesk/**, so nothing is rewritten. Privilege-based (privilege.json, service "admin",
                // HELPDESK_TICKET_*); the privilege filter maps the "helpdesk" segment to that service.
                .route(HelpdeskRouteConstants.ROUTE_ID, r -> r.path(HelpdeskRouteConstants.GATEWAY_PREFIX + "/**")
                        .filters(f -> guardedDirectRoute(f, HelpdeskRouteConstants.GATEWAY_PREFIX,
                                HelpdeskRouteConstants.CIRCUIT_BREAKER, HelpdeskRouteConstants.FALLBACK,
                                rateLimitEnabled, apiRateLimiter))
                        .uri(adminApiUri()))

                // ── SearchAPI — Swagger/OpenAPI docs ─────────────────────────
                // SearchAPI has no context-path (docs at bare /v3/api-docs), but the business route
                // below always inserts /api/v1/ regardless of segment -- so docs need their own
                // prefix-stripping route too. Declared before search-api so it wins for these paths.
                .route("search-docs", r -> r
                        .path("/api/search/v3/api-docs/**", "/api/search/swagger-ui/**", "/api/search/swagger-ui.html")
                        .filters(f -> f
                                .rewritePath("/api/search/(?<segment>.*)", "/${segment}")
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/search"))
                        .uri(searchApiUri()))

                .route("search-api", r -> r.path("/api/v1/search/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/search",
                                "search-cb", "search", rateLimitEnabled, apiRateLimiter))
                        .uri(searchApiUri()))

                // ── Rider microservices ──────────────────────────────────────
                // Each was built with ApiVersionConfig.addPathPrefix("/api/v1", ...) rather than
                // server.servlet.context-path (see each service's own class of that name) so a
                // future /api/v2 controller can run alongside today's /api/v1 ones without
                // re-versioning the whole service at once -- the externally-reachable shape is the
                // same /api/v1/... either way, so the rewrite here is identical to bookings-api's.
                // ── Rider microservices — Swagger/OpenAPI docs ───────────────
                // springdoc serves each at bare /v3/api-docs (ApiVersionConfig prefixes only
                // @RestControllers), but the standard rewrite below would turn
                // /api/rider/<svc>/v3/api-docs into /api/v1/v3/api-docs -- so each gets a
                // prefix-stripping docs route, declared first so it wins.
                .route("rider-pricing-docs", r -> docsRoute(r, "/api/rider/pricing", riderPricingApiUri()))
                .route("rider-location-docs", r -> docsRoute(r, "/api/rider/location", riderLocationApiUri()))
                .route("rider-dispatch-docs", r -> docsRoute(r, "/api/rider/dispatch", riderDispatchApiUri()))
                .route("rider-ride-docs", r -> docsRoute(r, "/api/rider/ride", riderRideApiUri()))
                .route("rider-payment-docs", r -> docsRoute(r, "/api/rider/payment", riderPaymentApiUri()))
                .route("rider-customer-docs", r -> docsRoute(r, "/api/rider/customer", riderCustomerApiUri()))
                .route("rider-workflow-docs", r -> docsRoute(r, "/api/rider/workflow", riderWorkflowApiUri()))
                .route("rider-onboarding-docs", r -> docsRoute(r, "/api/rider/onboarding", riderOnboardingApiUri()))
                .route("rider-train-docs", r -> docsRoute(r, "/api/rider/train", riderTrainApiUri()))
                .route("rider-commerce-docs", r -> docsRoute(r, "/api/rider/commerce", riderCommerceApiUri()))
                .route("rider-config-docs", r -> docsRoute(r, "/api/rider/config", riderConfigApiUri()))
                .route("rider-audit-docs", r -> docsRoute(r, "/api/rider/audit", riderAuditApiUri()))
                .route(WalletRouteConstants.DOCS_ROUTE_ID, r -> docsRoute(r, WalletRouteConstants.DOCS_PREFIX, riderWalletApiUri()))
                .route(AccountingRouteConstants.DOCS_ROUTE_ID, r -> docsRoute(r, AccountingRouteConstants.DOCS_PREFIX, riderAccountingApiUri()))
                .route(FranchiseRouteConstants.DOCS_ROUTE_ID, r -> docsRoute(r, FranchiseRouteConstants.DOCS_PREFIX, riderFranchiseApiUri()))
                .route("rider-driver-earnings-docs", r -> docsRoute(r, "/api/rider/driver-earnings", riderDriverEarningsApiUri()))

                .route("rider-pricing-api", r -> r.path("/api/v1/pricing/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/pricing",
                                "rider-pricing-cb", "rider-pricing", rateLimitEnabled, apiRateLimiter))
                        .uri(riderPricingApiUri()))

                .route("rider-location-api", r -> r.path("/api/v1/location/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/location",
                                "rider-location-cb", "rider-location", rateLimitEnabled, apiRateLimiter))
                        .uri(riderLocationApiUri()))

                .route("rider-dispatch-api", r -> r.path("/api/v1/dispatch/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/dispatch",
                                "rider-dispatch-cb", "rider-dispatch", rateLimitEnabled, apiRateLimiter))
                        .uri(riderDispatchApiUri()))

                // RideService's STOMP (/api/v1/ride/ws) and call-signaling (/api/v1/ride/ws-signal) sockets.
                // RideService serves exactly these paths, so nothing is rewritten. A ws:// scheme is
                // needed for the upgrade, and this route must precede the REST route below.
                .route("rider-ride-websocket", r -> r
                        .path("/api/v1/ride/ws", "/api/v1/ride/ws/**", "/api/v1/ride/ws-signal/**")
                        .filters(f -> f
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, "/api/v1/ride"))
                        .uri(riderRideWebSocketUri()))

                // Public live-trip tracking: GET only, no sign-in (pathmatchers.rider-ride-public-track), the share token in the
                // X-Share-Token header. Its own per-address limit runs BEFORE the breaker (as on the auth surface), so a flood
                // of anonymous polls is refused with 429. It has its own breaker and fallback, so failing public polls can never
                // open the breaker of the signed-in ride API (and vice versa). Any other method
                // on this path falls through to rider-ride-api (token required). Must precede rider-ride-api.
                .route(RidePublicRouteConstants.PUBLIC_TRACK_ROUTE_ID, r -> r.path(RidePublicRouteConstants.PUBLIC_TRACK_PATH)
                        .and().method(HttpMethod.GET)
                        .filters(f -> authRoute(f, "/api/v1/ride", RidePublicRouteConstants.PUBLIC_TRACK_CIRCUIT_BREAKER,
                                RidePublicRouteConstants.PUBLIC_TRACK_FALLBACK,
                                publicTrackRateLimitEnabled, publicTrackRateLimiter, publicTrackIpKeyResolver))
                        .uri(riderRideApiUri()))

                .route("rider-ride-api", r -> r.path("/api/v1/ride/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/ride",
                                "rider-ride-cb", "rider-ride", rateLimitEnabled, apiRateLimiter))
                        .uri(riderRideApiUri()))

                .route("rider-customer-api", r -> r.path("/api/v1/customer/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/customer",
                                "rider-customer-cb", "rider-customer", rateLimitEnabled, apiRateLimiter))
                        .uri(riderCustomerApiUri()))

                // Rider_WorkflowService — generic Flowable-backed review engine (platform
                // capability, not a domain owner). Not consumed through the gateway yet; routed
                // here so future consumers besides Rider_OnboardingService can reach it the same way.
                // /api/v1/workflow/internal/** (service-token only, called by Rider_OnboardingService) is refused here with 404.
                .route("rider-workflow-api", r -> r.path("/api/v1/workflow/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(WorkflowRouteConstants.INTERNAL_PATH));
                            return directRoute(f, "/api/v1/workflow",
                                    "rider-workflow-cb", "rider-workflow", rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(riderWorkflowApiUri()))

                // matrimonial-api serves /api/v1/matrimony/<path> itself, so the path is forwarded untouched. Authorization for
                // these paths is the service's own (it validates the caller's UAM token) plus the privilege.json entries
                // of service "matrimony" for the operations screens.
                // The STOMP socket route comes first and keeps the ws:// scheme the upgrade needs.
                .route("matrimony-websocket", r -> r
                        .path(MatrimonyRouteConstants.GATEWAY_PREFIX + "/ws-raw", MatrimonyRouteConstants.GATEWAY_PREFIX + "/ws-raw/**")
                        .filters(f -> f
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, MatrimonyRouteConstants.GATEWAY_PREFIX))
                        .uri(matrimonyWebSocketUri()))

                .route("matrimony-api", r -> r.path(MatrimonyRouteConstants.GATEWAY_PREFIX + "/**")
                        .filters(f -> directRoute(f,
                                MatrimonyRouteConstants.GATEWAY_PREFIX, "matrimony-cb", "matrimony", rateLimitEnabled, apiRateLimiter))
                        .uri(matrimonyApiUri()))

                // Food delivery API (food-delivery-platform/backend, not on the service registry): it serves /api/v1/food/<path>
                // itself. The admin paths (/api/v1/food/admin/**, the food console inside Rider_Web) are enforced by the
                // privilege.json entries of service "food"; customer, partner and driver paths need a signed-in UAM user, except
                // the public browse paths listed in the path matchers.
                .route(FoodRouteConstants.WEBSOCKET_ROUTE_ID, r -> r.path(FoodRouteConstants.WEBSOCKET_PATH)
                        .filters(f -> f
                                .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                                .addRequestHeader(FORWARDED_PREFIX_HEADER, FoodRouteConstants.API_PREFIX))
                        .uri(foodWebSocketUri()))

                .route(FoodRouteConstants.ROUTE_ID, r -> r.path(FoodRouteConstants.API_PREFIX + "/**")
                        .filters(f -> directRoute(f, FoodRouteConstants.API_PREFIX,
                                FoodRouteConstants.CIRCUIT_BREAKER, FoodRouteConstants.FALLBACK,
                                rateLimitEnabled, apiRateLimiter))
                        .uri(foodApiUri()))

                .route("rider-onboarding-api", r -> r.path("/api/v1/onboarding/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/onboarding",
                                "rider-onboarding-cb", "rider-onboarding", rateLimitEnabled, apiRateLimiter))
                        .uri(riderOnboardingApiUri()))

                // Rider_TrainService -- authorization for these paths is privilege-based (privilege.json,
                // service "rider-train"); the service itself performs no role checks.
                .route("rider-train-api", r -> r.path("/api/v1/train/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/train",
                                "rider-train-cb", "rider-train", rateLimitEnabled, apiRateLimiter))
                        .uri(riderTrainApiUri()))

                // Rider_CommerceService -- authorization is privilege-based (privilege.json, service "commerce");
                // only GET /api/v1/commerce/public/** (browsing a shop) is open. The service checks organization and ownership.
                .route("rider-commerce-api", r -> r.path("/api/v1/commerce/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/commerce",
                                "rider-commerce-cb", "rider-commerce", rateLimitEnabled, apiRateLimiter))
                        .uri(riderCommerceApiUri()))

                // Rider_ConfigService -- admin settings and the public app content. Authorization for the
                // admin paths is privilege-based (privilege.json, service "rider-config"); its /internal
                // paths (service credential only) are refused here with 404, and the service checks the credential too.
                .route("rider-config-api", r -> r.path("/api/v1/config/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/config",
                                "rider-config-cb", "rider-config", rateLimitEnabled, apiRateLimiter))
                        .uri(riderConfigApiUri()))

                // Rider_DriverEarningsService -- driver earnings, incentives and payout requests (driver app).
                // Authorization is privilege-based (privilege.json, service "rider-driver-earnings").
                .route("rider-driver-earnings-api", r -> r.path("/api/v1/driver-earnings/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/driver-earnings",
                                "rider-driver-earnings-cb", "rider-driver-earnings", rateLimitEnabled, apiRateLimiter))
                        .uri(riderDriverEarningsApiUri()))

                // Rider_AuditService -- authorization for the query paths is privilege-based (privilege.json,
                // service "rider-audit"). POST .../audit/screen-views has no catalog entry on purpose: it is
                // authenticated-only, open to every signed-in customer, driver or staff user. The service's
                // /audit/internal/** endpoints (service-token only) are refused here: they are never reachable
                // from outside the platform.
                .route("rider-audit-api", r -> r.path("/api/v1/audit/**")
                        .filters(f -> {
                            f.filter(com.heuristiq.gateway.audit.AuditPathRules.blockInternalAuditFilter());
                            return directRoute(f, "/api/v1/audit",
                                    "rider-audit-cb", "rider-audit", rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(riderAuditApiUri()))

                // Rider_ReportService -- authorization is privilege-based (privilege.json, service "rider-reports").
                .route("rider-reports-api", r -> r.path("/api/v1/reports/**")
                        .filters(f -> guardedDirectRoute(f, "/api/v1/reports",
                                "rider-reports-cb", "rider-reports", rateLimitEnabled, apiRateLimiter))
                        .uri(riderReportsApiUri()))

                // Rider_WalletService -- authorization is privilege-based (privilege.json, service "rider-wallet").
                // The customer (/me) and agent paths have their own privileges and sit under no wildcard entry. The
                // service's /wallet/internal/** endpoints (service-token only, called by app backends) are refused
                // here with 404: they are never reachable from outside the platform.
                .route(WalletRouteConstants.ROUTE_ID, r -> r.path(WalletRouteConstants.GATEWAY_PREFIX + "/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(WalletRouteConstants.INTERNAL_PATH));
                            return directRoute(f, WalletRouteConstants.GATEWAY_PREFIX,
                                    WalletRouteConstants.CIRCUIT_BREAKER, WalletRouteConstants.FALLBACK,
                                    rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(riderWalletApiUri()))

                // Rider_AccountingService -- authorization is privilege-based (privilege.json, service "rider-accounting").
                // Any /accounting/internal/** endpoint (service-token only) and the service's actuator are refused here
                // with 404: they are never reachable from outside the platform.
                .route(AccountingRouteConstants.ROUTE_ID, r -> r.path(AccountingRouteConstants.GATEWAY_PREFIX + "/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(AccountingRouteConstants.INTERNAL_PATH));
                            f.filter(InternalPathGuard.blockInternalPaths(AccountingRouteConstants.ACTUATOR_PATH));
                            return directRoute(f, AccountingRouteConstants.GATEWAY_PREFIX,
                                    AccountingRouteConstants.CIRCUIT_BREAKER, AccountingRouteConstants.FALLBACK,
                                    rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(riderAccountingApiUri()))

                // Rider_FranchiseService -- authorization is privilege-based (privilege.json, service "franchise").
                // Its /franchise/internal/** endpoints (service-token only, called by matrimonial-api and UAM) are
                // refused here with 404: they are never reachable from outside the platform.
                .route(FranchiseRouteConstants.ROUTE_ID, r -> r.path(FranchiseRouteConstants.GATEWAY_PREFIX + "/**")
                        .filters(f -> {
                            f.filter(InternalPathGuard.blockInternalPaths(FranchiseRouteConstants.INTERNAL_PATH));
                            f.filter(InternalPathGuard.blockInternalPaths(FranchiseRouteConstants.ACTUATOR_PATH));
                            return directRoute(f, FranchiseRouteConstants.GATEWAY_PREFIX,
                                    FranchiseRouteConstants.CIRCUIT_BREAKER, FranchiseRouteConstants.FALLBACK,
                                    rateLimitEnabled, apiRateLimiter);
                        })
                        .uri(riderFranchiseApiUri()))

                .build();
    }

    /**
     * Javadoc route: {@code <gatewayPrefix>/javadoc/<page>} -> the service's {@code /javadoc/<page>}
     * (static content packaged into its jar). A bare {@code .../javadoc} or {@code .../javadoc/}
     * opens {@code index.html}, since static resource handling doesn't resolve directory indexes.
     */
    private Buildable<Route> javadocRoute(PredicateSpec r, String gatewayPrefix, String uri) {
        String javadocPrefix = gatewayPrefix + "/javadoc";
        return r.path(javadocPrefix, javadocPrefix + "/**")
                .filters(f -> f
                        .filter((exchange, chain) -> {
                            String rest = exchange.getRequest().getURI().getRawPath().substring(javadocPrefix.length());
                            String target = (rest.isEmpty() || rest.equals("/")) ? "/javadoc/index.html" : "/javadoc" + rest;
                            ServerHttpRequest request = exchange.getRequest().mutate().path(target).build();
                            return chain.filter(exchange.mutate().request(request).build());
                        })
                        .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE))
                .uri(uri);
    }

    /**
     * Swagger/OpenAPI docs route for a service whose docs live at bare /v3/api-docs and
     * /swagger-ui: strips {@code gatewayPrefix} and passes it on as X-Forwarded-Prefix so springdoc
     * builds externally-reachable server URLs. No circuit breaker -- a down service just fails
     * its own docs fetch in the central Swagger UI.
     */
    private Buildable<Route> docsRoute(PredicateSpec r, String gatewayPrefix, String uri) {
        return r.path(gatewayPrefix + "/v3/api-docs", gatewayPrefix + "/v3/api-docs/**",
                        gatewayPrefix + "/swagger-ui/**", gatewayPrefix + "/swagger-ui.html")
                .filters(f -> f
                        .rewritePath(gatewayPrefix + "/(?<segment>.*)", "/${segment}")
                        .addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
                        .addRequestHeader(FORWARDED_PREFIX_HEADER, gatewayPrefix))
                .uri(uri);
    }

    /**
     * Filter chain for the rider microservices: each serves exactly the client-facing path
     * ({@code /api/v1/<service>/...}), so the request is forwarded unchanged -- no rewrite. Only the
     * shared resilience headers, circuit breaker and optional rate limiter are applied.
     */
    private GatewayFilterSpec directRoute(GatewayFilterSpec f, String gatewayPrefix, String circuitBreakerName,
            String fallbackPath, boolean rateLimitEnabled, RedisRateLimiter limiter) {
        applyResilience(f, circuitBreakerName, fallbackPath, gatewayPrefix);
        applyRateLimiterIfEnabled(f, rateLimitEnabled, limiter);
        return f;
    }

    /**
     * {@link #directRoute} behind the internal-path guard: a path that normalises to a service's service-token-only
     * {@code /internal} namespace ({@link InternalRouteConstants#SERVICE_INTERNAL_PATH}) is answered 404 and never forwarded.
     */
    private GatewayFilterSpec guardedDirectRoute(GatewayFilterSpec f, String gatewayPrefix, String circuitBreakerName,
            String fallbackPath, boolean rateLimitEnabled, RedisRateLimiter limiter) {
        f.filter(InternalPathGuard.blockInternalPaths(InternalRouteConstants.SERVICE_INTERNAL_PATH));
        return directRoute(f, gatewayPrefix, circuitBreakerName, fallbackPath, rateLimitEnabled, limiter);
    }

    /**
     * Filter chain of the auth surface (callers are not signed in): its own key resolver (the client address), and the rate limiter
     * BEFORE the circuit breaker, so a request the limiter refuses never reaches the breaker and an abusive burst cannot open the
     * breaker of the whole UAM service for everybody else.
     */
    private GatewayFilterSpec authRoute(GatewayFilterSpec f, String gatewayPrefix, String circuitBreakerName,
            String fallbackPath, boolean rateLimitEnabled, RedisRateLimiter limiter, KeyResolver keyResolver) {
        applyRateLimiter(f, rateLimitEnabled, limiter, keyResolver);
        applyResilience(f, circuitBreakerName, fallbackPath, gatewayPrefix);
        return f;
    }

    /**
     * Filter chain of the password login route: the usual resilience filters plus two rate limits, each in its own bucket: one per
     * client address and one shared by all callers. When the login limiters are not available but the auth limiter is, the route
     * keeps the auth surface's limit, so login is never less protected than before.
     */
    private GatewayFilterSpec loginRoute(GatewayFilterSpec f, boolean loginRateLimitEnabled, boolean authRateLimitEnabled) {
        // The limiters come first: a login the gateway refuses (429) never reaches the circuit breaker, so a burst of logins cannot
        // open the breaker of the whole UAM service and turn every other UAM call into a 503.
        if (loginRateLimitEnabled) {
            applyRateLimiter(f, true, loginRateLimiter, loginIpKeyResolver);
            applyRateLimiter(f, true, loginGlobalRateLimiter, loginGlobalKeyResolver);
        } else {
            applyRateLimiter(f, authRateLimitEnabled, authRateLimiter, authIpKeyResolver);
        }
        applyResilience(f, "uam-cb", "uam", "/api/v1/uam");
        return f;
    }

    /**
     * Filter chain of the public sign-up route: the auth surface's limit (as before) plus, when available, an hourly budget per
     * client address, each in its own bucket, both before the circuit breaker so refused sign-ups never count against UAM.
     */
    private GatewayFilterSpec registerRoute(GatewayFilterSpec f, boolean registerRateLimitEnabled, boolean authRateLimitEnabled) {
        applyRateLimiter(f, authRateLimitEnabled, authRateLimiter, authIpKeyResolver);
        applyRateLimiter(f, registerRateLimitEnabled, registerRateLimiter, registerIpKeyResolver);
        applyResilience(f, "uam-cb", "uam", "/api/v1/uam");
        return f;
    }

    /**
     * Builder-style helper applying the filter chain shared by every "standard" route:
     * version-aware path rewrite → {@code X-Forwarded-By} header → CORS header dedupe → circuit
     * breaker → conditional rate limiting. Equivalent to {@link #applyResilience} followed by
     * {@link #applyRateLimiterIfEnabled}, with the rewrite step prepended.
     *
     * @param f                  the in-progress filter spec to configure
     * @param gatewayPrefix      this route's public gateway prefix (e.g. "/api/payment") -- also
     *                           doubles as the {@code X-Forwarded-Prefix} value sent downstream so
     *                           springdoc can compute the correct externally-reachable server URL
     *                           in each service's own OpenAPI spec (see the centralized-Swagger
     *                           aggregator in {@code springdoc.swagger-ui.urls} / application.properties)
     * @param circuitBreakerName Resilience4j circuit breaker name for this route
     * @param fallbackPath       suffix appended to {@code forward:/fallback/} for the CB fallback
     * @param rateLimitEnabled   whether Redis rate limiting is active for this route
     * @param limiter            which {@link RedisRateLimiter} bean this route uses -- almost
     *                           always {@code apiRateLimiter}, except uam-auth-api which passes
     *                           the stricter {@code authRateLimiter}
     * @return the same spec, fully configured, for chaining into {@code .uri(...)}
     */
    private GatewayFilterSpec standardRoute(GatewayFilterSpec f, String gatewayPrefix, String circuitBreakerName,
            String fallbackPath, boolean rateLimitEnabled, RedisRateLimiter limiter) {
        f.filter(versionAwareRewrite(gatewayPrefix));
        applyResilience(f, circuitBreakerName, fallbackPath, gatewayPrefix);
        applyRateLimiterIfEnabled(f, rateLimitEnabled, limiter);
        return f;
    }

    /**
     * Replaces the old static {@code rewritePath("/api/foo/(?<segment>.*)", "/api/v1/${segment}")}
     * with a version-aware equivalent: peels an optional {@code /vN} segment off the client's path
     * (in either position -- see {@link RequestVersioning}, defaulting to v1 when absent) and
     * forwards to whatever {@link RequestVersioning.Resolved#backendPath()} computes -- which also
     * handles reinserting the literal segment ("uam", "payments") that uam-auth-api/uam-api/pay-api's
     * downstream needs but the client never sends. A plain regex replacement can't express "default
     * to v1 when the optional group didn't match" cleanly, hence a small programmatic filter instead
     * of {@code RewritePathGatewayFilterFactory}.
     */
    private GatewayFilter versionAwareRewrite(String gatewayPrefix) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getRawPath();
            RequestVersioning.Resolved resolved = RequestVersioning.resolve(path);
            String newPath;
            if (resolved != null) {
                newPath = resolved.backendPath();
            } else {
                // Defensive fallback -- every route's own gatewayPrefix is registered in
                // RequestVersioning's table, so this shouldn't be reachable in practice.
                String remainder = path.length() > gatewayPrefix.length() ? path.substring(gatewayPrefix.length() + 1) : "";
                newPath = remainder.isEmpty() ? "/api/" + RequestVersioning.DEFAULT_VERSION
                        : "/api/" + RequestVersioning.DEFAULT_VERSION + "/" + remainder;
            }

            ServerHttpRequest newRequest = exchange.getRequest().mutate().path(newPath).build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        };
    }

    /**
     * Applies the {@code X-Forwarded-By}/{@code X-Forwarded-Prefix} headers, CORS header dedupe,
     * circuit breaker and retry — the portion of the standard chain that every route (including
     * nms-api, which has no path rewrite) needs identically.
     */
    private GatewayFilterSpec applyResilience(GatewayFilterSpec f, String circuitBreakerName, String fallbackPath,
            String gatewayPrefix) {
        f.addRequestHeader(FORWARDED_BY_HEADER, FORWARDED_BY_VALUE)
         .addRequestHeader(FORWARDED_PREFIX_HEADER, gatewayPrefix)
         .dedupeResponseHeader(
                 CORS_DEDUPE_HEADERS,
                 CORS_DEDUPE_STRATEGY)
         // The circuit breaker is declared first so it wraps the retry below: the retry only repeats the call to the
         // service, and the breaker's fallback answer (a 503 made by the gateway) is never retried. An open breaker
         // answers with its fallback at once, without any try at all.
         .circuitBreaker(c -> c
                 .setName(circuitBreakerName)
                 .setFallbackUri(FALLBACK_URI_PREFIX + fallbackPath))
         // A read that hits an instance that is going away (a deploy) is tried again on another one; GET only, so no
         // write is ever repeated. See RouteRetryConstants for exactly what is retried.
         .retry(r -> r
                 .setRetries(RouteRetryConstants.RETRIES)
                 .setMethods(org.springframework.http.HttpMethod.GET)
                 .setSeries()
                 .setStatuses(RouteRetryConstants.RETRY_STATUSES.toArray(org.springframework.http.HttpStatus[]::new))
                 .setExceptions(retryExceptions())
                 .setBackoff(RouteRetryConstants.FIRST_BACKOFF, RouteRetryConstants.MAX_BACKOFF,
                         RouteRetryConstants.BACKOFF_FACTOR, true));
        return f;
    }

    /** {@link RouteRetryConstants#RETRY_EXCEPTIONS} as the array the retry filter's varargs setter takes. */
    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable>[] retryExceptions() {
        return RouteRetryConstants.RETRY_EXCEPTIONS.toArray(new Class[0]);
    }

    /** Applies the Redis request rate limiter filter using the given limiter bean, only when enabled. */
    private void applyRateLimiterIfEnabled(GatewayFilterSpec f, boolean rateLimitEnabled, RedisRateLimiter limiter) {
        applyRateLimiter(f, rateLimitEnabled, limiter, userOrIpKeyResolver);
    }

    /** Applies the Redis request rate limiter filter with the given limiter and key resolver, only when enabled. */
    private void applyRateLimiter(GatewayFilterSpec f, boolean rateLimitEnabled, RedisRateLimiter limiter, KeyResolver keyResolver) {
        if (rateLimitEnabled) {
            f.requestRateLimiter(rl -> rl
                    .setRateLimiter(limiter)
                    .setKeyResolver(keyResolver));
        }
    }

    /**
     * Client-facing URL versioning, local to this class (deliberately not a shared component --
     * {@code PrivilegeBasedAccessFilter} keeps its own identical copy of this same logic, since it
     * resolves the same information independently rather than sharing a bean with this class).
     * Accepts the version segment in either position:
     * <ul>
     *   <li>{@code /api/<service>/v{N}/<rest>} — version right after the service's own gateway
     *       prefix; the original, no-new-routes-needed shape.</li>
     *   <li>{@code /api/v{N}/<service>/<rest>} — version right after {@code /api}, before the
     *       service name; each route's {@code .path(...)} predicate above registers both forms.</li>
     * </ul>
     * Both resolve to the identical {@link Resolved#backendPath()}. Absent version -> {@link #DEFAULT_VERSION}.
     */
    private static final class RequestVersioning {

        static final String DEFAULT_VERSION = "v1";
        private static final String API_PREFIX = "/api";
        private static final java.util.regex.Pattern VERSION_SEGMENT = java.util.regex.Pattern.compile("^v(\\d+)$");

        /** One row per client-facing gateway prefix, WITHOUT the leading "/api". {@code service}
         *  matches the circuit-breaker fallbackPath names used elsewhere in this class (admin,
         *  bookings, uam, ...) so the two stay in lockstep by construction. {@code downstreamInsert}
         *  is null for every "plain" route. NMS's legacy /api/v1/nms/** route needs no separate
         *  entry -- version-first parsing decomposes it into version "v1" + this same "nms" bare
         *  prefix automatically. */
        private record PrefixEntry(String barePrefix, String service, String downstreamInsert) {}

        /** The AdminAPI service slug in {@link #SERVICE_PREFIXES}. */
        private static final String ADMIN_SERVICE = "admin";

        /**
         * AdminAPI's controllers live under {@code /api/v1/admin/...}, so the {@code admin} prefix of a client path is
         * kept when forwarding ({@code downstreamInsert}). These few AdminAPI endpoints are served WITHOUT it (public
         * vendor registration and the payment-provider webhooks, both permitted without a login in AdminAPI's own
         * security config), so they keep the plain {@code /api/v1/<remainder>} path.
         */
        private static boolean servedOutsideAdminNamespace(String remainder) {
            return "vendors/register".equals(remainder) || "webhooks".equals(remainder) || remainder.startsWith("webhooks/");
        }

        private static final java.util.List<PrefixEntry> SERVICE_PREFIXES = java.util.List.of(
                new PrefixEntry("admin", "admin", "admin"),
                new PrefixEntry("bookings", "bookings", null),
                new PrefixEntry("uam", "uam", "uam"),
                new PrefixEntry("dms", "dms", null),
                new PrefixEntry("irctc", "irctc", null),
                new PrefixEntry("payment", "payment", "payments"),
                new PrefixEntry("search", "search", null),
                new PrefixEntry("nms", "nms", "nms")
        );

        record Resolved(String prefix, String service, String version, String remainder, String downstreamInsert) {
            /** Keeps the plain backend path for the AdminAPI endpoints that are not under /api/v1/admin. */
            Resolved {
                if (downstreamInsert != null && ADMIN_SERVICE.equals(service) && servedOutsideAdminNamespace(remainder)) {
                    downstreamInsert = null;
                }
            }

            /** The path this request projects to on the backend, after the gateway's rewrite. */
            String backendPath() {
                StringBuilder sb = new StringBuilder("/api/").append(version);
                if (downstreamInsert != null) {
                    sb.append('/').append(downstreamInsert);
                }
                if (!remainder.isEmpty()) {
                    sb.append('/').append(remainder);
                }
                return sb.toString();
            }
        }

        private record PrefixMatch(PrefixEntry entry, String remainder) {}

        /** Resolves {@code path} against the known service prefixes, in EITHER supported version
         *  position. Returns {@code null} when {@code path} isn't under {@code /api} or doesn't
         *  match any known service. */
        static Resolved resolve(String path) {
            if (!path.equals(API_PREFIX) && !path.startsWith(API_PREFIX + "/")) {
                return null;
            }
            String afterApi = path.substring(API_PREFIX.length());

            Segment firstAfterApi = firstSegment(afterApi);
            if (firstAfterApi != null) {
                java.util.regex.Matcher m = VERSION_SEGMENT.matcher(firstAfterApi.value());
                if (m.matches()) {
                    PrefixMatch pm = matchBarePrefix(firstAfterApi.rest());
                    if (pm != null) {
                        return new Resolved(API_PREFIX + "/" + pm.entry().barePrefix(), pm.entry().service(),
                                "v" + m.group(1), pm.remainder(), pm.entry().downstreamInsert());
                    }
                }
            }

            PrefixMatch pm = matchBarePrefix(afterApi);
            if (pm == null) {
                return null;
            }
            Segment firstAfterPrefix = firstSegment(pm.remainder().isEmpty() ? "" : "/" + pm.remainder());
            if (firstAfterPrefix != null) {
                java.util.regex.Matcher m = VERSION_SEGMENT.matcher(firstAfterPrefix.value());
                if (m.matches()) {
                    return new Resolved(API_PREFIX + "/" + pm.entry().barePrefix(), pm.entry().service(),
                            "v" + m.group(1), firstAfterPrefix.rest().isEmpty() ? "" : firstAfterPrefix.rest().substring(1),
                            pm.entry().downstreamInsert());
                }
            }
            return new Resolved(API_PREFIX + "/" + pm.entry().barePrefix(), pm.entry().service(),
                    DEFAULT_VERSION, pm.remainder(), pm.entry().downstreamInsert());
        }

        private static PrefixMatch matchBarePrefix(String afterApi) {
            PrefixEntry entry = SERVICE_PREFIXES.stream()
                    .filter(p -> afterApi.equals("/" + p.barePrefix()) || afterApi.startsWith("/" + p.barePrefix() + "/"))
                    .max(java.util.Comparator.comparingInt(p -> p.barePrefix().length()))
                    .orElse(null);
            if (entry == null) {
                return null;
            }
            String prefixWithSlash = "/" + entry.barePrefix();
            String remainder = afterApi.length() > prefixWithSlash.length()
                    ? afterApi.substring(prefixWithSlash.length() + 1) : "";
            return normalizeAdminRemainder(new PrefixMatch(entry, remainder));
        }

        /**
         * AdminAPI's own controllers are all mapped under "/api/v1/admin/...", so the client-facing
         * path must carry an "admin" segment TWICE: once as the gateway's own service prefix
         * ("/api/admin/..."), once again as the remainder AdminController's own @RequestMapping
         * expects ("admin/dashboard/summary", not "dashboard/summary"). Some callers (notably
         * AdminWeb's federated Dashboard component, when it accidentally resolves Rider_Web's host
         * environment.apiUrl -- which has no "/admin" suffix -- instead of its own) only ever send
         * the segment once ("/api/admin/dashboard/summary"). Rather than requiring every caller to
         * get this exactly right, normalize here so either shape resolves to the same backend path:
         * strip a leading "admin/" (or bare "admin") from the remainder if present, then always
         * re-add exactly one. Idempotent -- a request that already included it unchanged.
         */
        private static PrefixMatch normalizeAdminRemainder(PrefixMatch pm) {
            if (!"admin".equals(pm.entry().service())) {
                return pm;
            }
            String remainder = pm.remainder();
            if (remainder.equals("admin")) {
                remainder = "";
            } else if (remainder.startsWith("admin/")) {
                remainder = remainder.substring("admin/".length());
            }
            remainder = remainder.isEmpty() ? "admin" : "admin/" + remainder;
            return new PrefixMatch(pm.entry(), remainder);
        }

        private record Segment(String value, String rest) {}

        private static Segment firstSegment(String pathWithLeadingSlash) {
            if (pathWithLeadingSlash.isEmpty()) {
                return null;
            }
            String noLeadingSlash = pathWithLeadingSlash.substring(1);
            int slash = noLeadingSlash.indexOf('/');
            return slash >= 0
                    ? new Segment(noLeadingSlash.substring(0, slash), noLeadingSlash.substring(slash))
                    : new Segment(noLeadingSlash, "");
        }
    }
}
