package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.common.constants.MessageConstants;
import com.heuristiq.gateway.config.FoodRouteConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.HelpdeskRouteConstants;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fine-grained, data-driven RBAC guard — order -79. The sole gateway-level path-authorization
 * mechanism (replaces the earlier {@code RoleBasedAccessFilter}, which compared a single
 * role-string header against a hand-maintained {@code railcab.gateway.rbac.path-roles} YAML map —
 * removed entirely, no role-name comparisons happen anywhere in the gateway now).
 *
 * <p>Entirely data-driven off {@code privilege.json}: each entry names a numeric
 * {@code privilegeId}, a backend-form {@code apipath} pattern, the {@code service} it belongs to,
 * and an {@code operation} (mapped to an HTTP method). {@code apipath} is written in terms of what
 * the target service actually receives after the gateway's rewrite (e.g.
 * {@code /api/v1/uam/users/{id}}), not the path the client calls -- the version is a literal part
 * of that string, so a v2 counterpart of the same endpoint is just a separate entry with its own
 * {@code /api/v2/...} apipath. Every service's own {@code ApiVersionConfig} prepends the same
 * literal {@code /api/v1}, so {@code service} is what tells two services' otherwise-identical-
 * looking backend paths apart (see the nested {@link RequestVersioning}). Unlike
 * an earlier version of this filter, the caller's granted privilege numbers are
 * <b>not</b> baked into the JWT at login time -- {@link RolePrivilegeCacheService} resolves them
 * live, per request, straight from UAM's {@code uam_roles}/{@code permissions} collections (behind
 * a short-TTL cache), keyed off every role in the {@value GatewayConstants#X_USER_ROLES} header the
 * JWT filter already injects -- a caller with more than one role is granted the union of all of
 * their roles' privileges, not just the first. This means a permission change a SUPER_ADMIN makes
 * via RoleController takes effect for already-logged-in users within one cache TTL window, not just
 * on their next login.
 *
 * <p>A request matches the *most specific* catalog entry (by path pattern, Spring's own
 * {@link AntPathMatcher} specificity ordering) whose method also matches. If no entry matches
 * the request, it passes through unchanged -- this filter only tightens paths it actually knows
 * about; anything outside privilege.json's catalog is authenticated-only. Internal service tokens
 * bypass this filter entirely.
 */
@Slf4j
@Component
public class PrivilegeBasedAccessFilter implements GlobalFilter, Ordered {

    private final GatewayProperties properties;
    private final PrivilegeCatalogLoader catalogLoader;
    private final PathMatchersConfig pathMatchersConfig;
    private final RolePrivilegeCacheService rolePrivilegeCacheService;
    private final ObjectMapper objectMapper;
    private final Scheduler blockingOpsScheduler;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private PrivilegeCatalogIndex catalog = PrivilegeCatalogIndex.empty();
    private List<String> publicPathPatterns = List.of();
    /** Public for GET only (PathMatchersConfig#publicGetOnlyPaths); other methods go through the catalog as usual. */
    private List<String> publicGetOnlyPathPatterns = List.of();

    // OpenAPI docs / Swagger UI -- bypassed here for the same reason ReactiveJwtAuthenticationFilter
    // bypasses them: these paths are unauthenticated (no X-User-Roles header at all reaches this
    // filter for them), so without this check any catalog entry whose pattern happens to be broad
    // enough to also match a docs path (e.g. a catch-all "/api/admin/**" privilege) would resolve an
    // empty granted-privilege set for the anonymous caller and deny with 403 -- exactly what
    // happened before this fix. Mirrors the same path list as ReactiveJwtAuthenticationFilter /
    // GatewaySecurityConfig: the shared list is GatewayConstants.DOCS_PATH_PATTERNS (checked in filter()).

    public PrivilegeBasedAccessFilter(GatewayProperties properties,
                                      PrivilegeCatalogLoader catalogLoader,
                                      PathMatchersConfig pathMatchersConfig,
                                      RolePrivilegeCacheService rolePrivilegeCacheService,
                                      ObjectMapper objectMapper,
                                      Scheduler blockingOpsScheduler) {
        this.properties = properties;
        this.catalogLoader = catalogLoader;
        this.pathMatchersConfig = pathMatchersConfig;
        this.rolePrivilegeCacheService = rolePrivilegeCacheService;
        this.objectMapper = objectMapper;
        this.blockingOpsScheduler = blockingOpsScheduler;
    }

    @PostConstruct
    void loadCatalog() {
        // Indexed once by service and method, so a request only pattern-matches its own service's entries.
        catalog = PrivilegeCatalogIndex.of(catalogLoader.load());
        log.info("Loaded {} privilege entries for gateway path authorization", catalog.size());

        // pathmatchers.* (GatewaySecurityConfig#resolvePublicPaths / ReactiveJwtAuthenticationFilter)
        // are paths the gateway lets through with NO JWT at all -- webhook postbacks (Razorpay/PayU/
        // Toucan), the login/register/OTP surface itself, public vendor registration, the ride
        // websocket handshake, health/simulator endpoints. Those callers never carry an
        // X-User-Roles header, so without this same bypass here, any catalog entry whose pattern
        // happens to also cover one of these paths (e.g. RIDER_PAYMENT_*'s broad "/api/v1/payment/**"
        // matching the Razorpay webhook at "/api/v1/payment/payments/webhook")
        // would resolve an empty granted-privilege set and 403 a request the rest of the gateway
        // deliberately treats as anonymous/public.
        publicPathPatterns = Stream.of(
                        pathMatchersConfig.getBookingsHealth(),
                        pathMatchersConfig.getIrctcHealth(),
                        pathMatchersConfig.getIrctcSimulator(),
                        pathMatchersConfig.getUamIdentityAuth(),
                        pathMatchersConfig.getUamIdentityAuthVersioned(),
                        pathMatchersConfig.getUamIdentityAuthVersionAfter(),
                        pathMatchersConfig.getAdminVendorPublic(),
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
                        pathMatchersConfig.getActuator())
                .filter(StringUtils::hasText)
                .flatMap(pattern -> pattern.endsWith("/**")
                        ? Stream.of(pattern)
                        : Stream.of(pattern, pattern + "/**"))
                .toList();
        publicGetOnlyPathPatterns = List.copyOf(pathMatchersConfig.publicGetOnlyPaths());
    }

    @Override
    public int getOrder() {
        return -79;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // The bookings monolith is retired. Reject its old URLs before public/internal/RBAC bypasses;
        // otherwise removing its catalog would make those endpoints authenticated-only.
        RequestVersioning.Resolved destination = RequestVersioning.resolve(exchange.getRequest().getURI().getPath());
        if (destination != null && "bookings".equals(destination.service())) {
            exchange.getResponse().setStatusCode(HttpStatus.GONE);
            return exchange.getResponse().setComplete();
        }
        if (!properties.getRbac().isEnabled()) {
            return chain.filter(exchange);
        }

        String requestUri = exchange.getRequest().getURI().getPath();
        if (GatewayConstants.DOCS_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri))
                || publicPathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri))
                || (HttpMethod.GET.equals(exchange.getRequest().getMethod())
                        && publicGetOnlyPathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri)))) {
            return chain.filter(exchange);
        }

        // Every role the caller holds (see ReactiveJwtAuthenticationFilter) -- falls back to the
        // single-role header if X-User-Roles is somehow absent (e.g. an older/other caller of this
        // filter chain), so this never regresses to "no roles at all".
        Set<String> roles = rolesFrom(exchange);
        if (roles.contains(GatewayConstants.INTERNAL_SERVICE_ROLE)) {
            return chain.filter(exchange);
        }

        // One cached lookup of everything the caller's roles may do. It decides this endpoint's privilege and
        // whether the request is forwarded as "elevated" (see GatewayConstants.X_USER_ELEVATED). If the lookup
        // fails, an endpoint outside the catalog still passes (unelevated); a catalogued endpoint fails as before.
        return Mono.fromCallable(() -> Optional.of(rolePrivilegeCacheService.resolvePrivilegeIds(roles)))
                .subscribeOn(blockingOpsScheduler)
                .onErrorResume(e -> {
                    log.warn("Privilege lookup failed for roles={}: {}", roles, e.toString());
                    return Mono.just(Optional.<Set<Integer>>empty());
                })
                .flatMap(granted -> authorize(exchange, chain, roles, granted));
    }

    /** Applies the privilege decision for one request, then forwards it (elevated when the caller qualifies). */
    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain, Set<String> roles,
                                 Optional<Set<Integer>> lookup) {
        String uri = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // privilege.json's apipath is written in BACKEND form (e.g. "/api/v1/uam/users/{id}") --
        // the path the target service receives after the gateway's rewrite, not the client's own
        // path. Project the incoming request the same way RouteConfig's versionAwareRewrite does
        // (peeling an optional /vN segment, defaulting to v1, reinserting any literal segment like
        // "uam" the client never sends) so apipath can be matched directly against it. Every
        // service's own ApiVersionConfig prepends the SAME literal "/api/v1", so the projected path
        // alone can't tell two services apart -- "service" is what does (see CompiledPrivilege).
        // A path outside every known gateway prefix resolves to null and is matched unchanged,
        // service-less (matches only entries that likewise carry no service -- see matches() below).
        RequestVersioning.Resolved resolved = RequestVersioning.resolve(uri);
        String matchUri = resolved != null ? resolved.backendPath() : uri;
        String service = resolved != null ? resolved.service() : null;
        WebSearchPaths.Source webList = WebSearchPaths.source(matchUri, method);
        if (webList != null) {
            if (!webList.applicationCodes().isEmpty() && !roles.contains("SUPER_ADMIN")) {
                Object verifiedApps = exchange.getAttribute(com.heuristiq.gateway.auth.provider.CallerDetails.APPLICATIONS_ATTRIBUTE);
                if (!(verifiedApps instanceof List<?> apps) || apps.stream().noneMatch(app ->
                        webList.applicationCodes().stream().anyMatch(code -> code.equalsIgnoreCase(String.valueOf(app)))))
                    return sendForbidden(exchange);
            }
            matchUri = webList.path();
            service = webList.service();
            method = HttpMethod.GET;
        }
        String directList = DirectGetAllPaths.source(matchUri, method, service);
        if (directList != null) {
            matchUri = directList;
            method = HttpMethod.GET;
        }
        String franchiseList = FranchiseSearchPaths.source(matchUri, method);
        if (franchiseList != null) {
            matchUri = franchiseList;
            method = HttpMethod.GET;
            service = "franchise";
        }
        exchange.getAttributes().put(AuditExchangeAttributes.SERVICE, service == null ? "" : service);

        List<CompiledPrivilege> matching = catalog.matching(pathMatcher, matchUri, method, service);

        if (matching.isEmpty()) {
            // Explicit customer list contracts preserve their source's authenticated-only access.
            // SearchAPI separately seals tenant/user scope; unknown WEB models remain denied.
            if (webList != null && webList.authenticatedRead()) return forward(exchange, chain, lookup.orElse(Set.of()));
            if (franchiseList != null || directList != null || webList != null) return sendForbidden(exchange);
            return forward(exchange, chain, lookup.orElse(Set.of()));
        }
        if (lookup.isEmpty()) {
            return Mono.error(new IllegalStateException("Privilege lookup unavailable"));
        }
        Set<Integer> granted = lookup.get();

        // Multiple catalog entries commonly share the identical apipath (e.g. a *_READ/*_MANAGE
        // pair both scoped to the same "/api/foo/**"), so picking a single "most specific" entry
        // and requiring exactly that one privilegeId was wrong: a caller granted only *_MANAGE
        // (not the paired *_READ) would be incorrectly denied on a GET, since a GET also matches
        // *_READ's method filter and the two entries tie on pattern specificity -- the single-match
        // version resolved that tie by catalog/file order, not by what the caller actually holds.
        // Fix: find the most-specific pattern among all matches, collect every entry tied at that
        // specificity (not just the first), and allow if the caller holds ANY of those privilegeIds.
        // A more specific privilege elsewhere in the catalog (e.g. a single-endpoint override) still
        // takes precedence over a broader wildcard, since only entries at the winning specificity
        // level are considered.
        Comparator<String> patternComparator = pathMatcher.getPatternComparator(matchUri);
        String mostSpecificPattern = matching.stream()
                .map(entry -> entry.pattern)
                .min(patternComparator)
                .orElseThrow();
        List<CompiledPrivilege> decisive = matching.stream()
                .filter(entry -> patternComparator.compare(entry.pattern, mostSpecificPattern) == 0)
                .toList();
        Set<Integer> candidatePrivilegeIds = decisive.stream()
                .map(entry -> entry.privilegeId)
                .collect(Collectors.toUnmodifiableSet());
        // Facts for the access-audit publisher (AuditEventPublisher): which privileges decided this request.
        decisive.stream().map(entry -> entry.module).filter(Objects::nonNull).findFirst()
                .ifPresent(module -> exchange.getAttributes().put(AuditExchangeAttributes.MODULE, module));
        exchange.getAttributes().put(AuditExchangeAttributes.PRIVILEGE_IDS, candidatePrivilegeIds);

        // Live lookup (behind a short-TTL cache) -- resolves the union of ALL the caller's roles'
        // CURRENT permissions from UAM's database rather than trusting a snapshot baked into the
        // token at login, and without dropping every role but the first.
        boolean allowed = candidatePrivilegeIds.stream().anyMatch(granted::contains);
        if (allowed) {
            exchange.getAttributes().put(AuditExchangeAttributes.PRIVILEGE_IDS,
                    candidatePrivilegeIds.stream().filter(granted::contains).collect(Collectors.toUnmodifiableSet()));
        } else {
            exchange.getAttributes().put(AuditExchangeAttributes.DENIED, Boolean.TRUE);
        }
        if (!allowed) {
            log.warn("Privilege denied: candidatePrivilegeIds={} uri={} method={} roles={} granted={}",
                    candidatePrivilegeIds, uri, method, roles, granted);
            return sendForbidden(exchange);
        }
        return forward(exchange, chain, granted);
    }

    /** Forwards the request, adding {@code X-User-Elevated: true} when the caller holds the elevated-access privilege. */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, Set<Integer> granted) {
        if (!granted.contains(GatewayConstants.ELEVATED_ACCESS_PRIVILEGE_ID)) {
            return chain.filter(exchange);
        }
        var request = exchange.getRequest().mutate()
                .headers(h -> h.set(GatewayConstants.X_USER_ELEVATED, Boolean.TRUE.toString()))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Set<String> rolesFrom(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String rolesHeader = headers.getFirst(GatewayConstants.X_USER_ROLES);
        if (StringUtils.hasText(rolesHeader)) {
            return Arrays.stream(rolesHeader.split(GatewayConstants.ROLE_LIST_DELIMITER))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        String role = headers.getFirst(GatewayConstants.X_USER_ROLE);
        return StringUtils.hasText(role) ? Set.of(role) : Set.of();
    }

    private Mono<Void> sendForbidden(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                GatewayConstants.JSON_ERROR_KEY, MessageConstants.FORBIDDEN,
                GatewayConstants.JSON_MESSAGE_KEY, MessageConstants.ACCESS_NOT_ALLOWED);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    /**
     * Client-facing URL versioning, local to this class (deliberately not a shared component --
     * {@code RouteConfig} keeps its own identical copy of this same logic, since it resolves the
     * same information independently rather than sharing a bean with this class). Accepts the
     * version segment in either position:
     * <ul>
     *   <li>{@code /api/<service>/v{N}/<rest>} — version right after the service's own gateway
     *       prefix; the original, no-new-routes-needed shape.</li>
     *   <li>{@code /api/v{N}/<service>/<rest>} — version right after {@code /api}, before the
     *       service name; RouteConfig's routes register both forms.</li>
     * </ul>
     * Both resolve to the identical {@link Resolved#backendPath()}. Absent version -> {@link #DEFAULT_VERSION}.
     */
    private static final class RequestVersioning {

        static final String DEFAULT_VERSION = "v1";
        private static final String API_PREFIX = "/api";
        /** Services that serve exactly the client-facing path {@code /api/v1/<service>/...} (no gateway rewrite). */
        private static final java.util.regex.Pattern DIRECT_SERVICE_PATH = java.util.regex.Pattern.compile(
                "^/api/v(\\d+)/(ride|customer|dispatch|payment|onboarding|workflow|location|config|train|pricing|audit|driver-earnings|wallet|accounting|franchise"
                        + "|admin|uam|dms|search|nms|" + HelpdeskRouteConstants.PATH_SEGMENT + "|" + FoodRouteConstants.PATH_SEGMENT + ")(/.*)?$");
        /** Of those, the Rider_* microservices, whose catalog service slug is {@code rider-<name>}. */
        private static final java.util.Set<String> RIDER_SERVICES = java.util.Set.of("ride", "customer", "dispatch",
                "payment", "onboarding", "workflow", "location", "config", "train", "pricing", "audit", "driver-earnings", "wallet",
                "accounting");
        /** Path segments served by another service than their name says: their catalog service slug. The help desk
         *  ({@code /api/v1/helpdesk/**}) is served by Rider_AdminAPI, whose catalog entries carry service "admin". */
        private static final Map<String, String> SEGMENT_SERVICE_OVERRIDES =
                Map.of(HelpdeskRouteConstants.PATH_SEGMENT, HelpdeskRouteConstants.CATALOG_SERVICE);
        private static final java.util.regex.Pattern VERSION_SEGMENT = java.util.regex.Pattern.compile("^v(\\d+)$");

        /** One row per LEGACY client-facing gateway prefix (the BookingsAPI / IRCTC / old payment-gateway
         *  routes that still rewrite), WITHOUT the leading "/api". Delete this table, {@link #resolve}'s
         *  legacy branch and RouteConfig's {@code standardRoute} together with those routes when
         *  BookingsAPI is removed. Everything else is matched by {@link #DIRECT_SERVICE_PATH}. */
        private record PrefixEntry(String barePrefix, String service, String downstreamInsert) {}

        private static final List<PrefixEntry> SERVICE_PREFIXES = List.of(
                new PrefixEntry("bookings", "bookings", null),
                new PrefixEntry("irctc", "irctc", null),
                new PrefixEntry("payment", "payment", "payments")
        );

        record Resolved(String prefix, String service, String version, String remainder, String downstreamInsert) {
            /** The path this request projects to on the backend, after the gateway's rewrite --
             *  what privilege.json's {@code apipath} is now written in terms of. */
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
         *  match any known service -- callers should treat it as unversioned, service-less, and
         *  unchanged. */
        static Resolved resolve(String path) {
            if (!path.equals(API_PREFIX) && !path.startsWith(API_PREFIX + "/")) {
                return null;
            }
            // Services serve exactly the client-facing path (/api/v1/<service>/...), so the backend path is
            // the request path itself.
            java.util.regex.Matcher direct = DIRECT_SERVICE_PATH.matcher(path);
            if (direct.matches()) {
                String version = "v" + direct.group(1);
                String name = direct.group(2);
                String rest = direct.group(3) == null ? "" : direct.group(3).substring(1);
                String slug = SEGMENT_SERVICE_OVERRIDES.getOrDefault(name,
                        RIDER_SERVICES.contains(name) ? "rider-" + name : name);
                return new Resolved(API_PREFIX + "/" + version + "/" + name, slug, version, rest, name);
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
                    .max(Comparator.comparingInt(p -> p.barePrefix().length()))
                    .orElse(null);
            if (entry == null) {
                return null;
            }
            String prefixWithSlash = "/" + entry.barePrefix();
            String remainder = afterApi.length() > prefixWithSlash.length()
                    ? afterApi.substring(prefixWithSlash.length() + 1) : "";
            return new PrefixMatch(entry, remainder);
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
