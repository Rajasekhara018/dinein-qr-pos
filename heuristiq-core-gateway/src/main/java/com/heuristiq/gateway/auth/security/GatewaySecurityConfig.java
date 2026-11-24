package com.heuristiq.gateway.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.auth.filter.ReactiveJwtAuthenticationFilter;
import com.heuristiq.gateway.auth.provider.AuthProviderRouter;
import com.heuristiq.gateway.auth.service.UserStatusCacheService;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class GatewaySecurityConfig {

    private final AuthProviderRouter authProviderRouter;
    private final UserStatusCacheService userStatusCacheService;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final PathMatchersConfig pathMatchersConfig;
    private final Environment env;

    // OpenAPI docs / Swagger UI paths, exactly as ReactiveJwtAuthenticationFilter and
    // PrivilegeBasedAccessFilter also exempt them (see those classes) -- kept in their own
    // SecurityWebFilterChain, evaluated before the catch-all chain below, purely so this one place
    // can relax the framing headers (X-Frame-Options / CSP frame-ancestors) for just these paths
    // without touching the security posture of every other route. RailCab_Web embeds the
    // aggregator inline via iframe (see ApiDocs component), which a same-origin-only
    // X-Frame-Options: DENY (Spring Security's default) would otherwise block outright.
    // The path list is GatewayConstants.DOCS_PATH_PATTERNS (used by swaggerSecurityWebFilterChain below).

    @Bean
    @Order(1)
    public SecurityWebFilterChain swaggerSecurityWebFilterChain(ServerHttpSecurity http) {
        String frameAncestors = gatewayProperties.getSwagger().getFrameAncestors();
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(GatewayConstants.DOCS_PATH_PATTERNS.toArray(String[]::new)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .headers(headers -> headers
                        // Default is DENY, which blocks framing from any origin, including
                        // RailCab_Web's own -- CSP's frame-ancestors below is the actual allow-list,
                        // this just gets X-Frame-Options out of its way (modern browsers prefer
                        // frame-ancestors when both are present, but don't rely on that alone).
                        .frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "frame-ancestors 'self' " + frameAncestors)))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, Scheduler blockingOpsScheduler) {
        String[] publicPaths = resolvePublicPaths();
        String[] publicGetOnlyPaths = pathMatchersConfig.publicGetOnlyPaths().toArray(String[]::new);

        // Constructed directly (not autowired as a bean) so it exists only inside this security
        // chain — see ReactiveJwtAuthenticationFilter's class javadoc for why making it a
        // @Component previously caused it to also run as a duplicate, mis-ordered global filter.
        ReactiveJwtAuthenticationFilter jwtFilter = new ReactiveJwtAuthenticationFilter(
                authProviderRouter, gatewayProperties, pathMatchersConfig, userStatusCacheService, objectMapper,
                blockingOpsScheduler);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Stateless — no session stored at gateway
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return Mono.empty();
                        })
                        .accessDeniedHandler((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return Mono.empty();
                        })
                )
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers(publicPaths).permitAll();
                    // GET-only public paths (e.g. public trip tracking): any other method stays authenticated.
                    if (publicGetOnlyPaths.length > 0) {
                        exchanges.pathMatchers(HttpMethod.GET, publicGetOnlyPaths).permitAll();
                    }
                    exchanges.anyExchange().authenticated();
                })
                // Our custom filter runs before Spring's own authentication filter
                .addFilterBefore(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // More specific pattern registered first -- UrlBasedCorsConfigurationSource matches in
        // insertion order, so /api/payment/callback/** must win before the /** catch-all is
        // ever reached, or its own (narrower) origin list would never apply.
        source.registerCorsConfiguration("/api/payment/callback/**",
                buildCorsConfig(gatewayProperties.getCors().getPaymentCallbackAllowedOrigins()));
        source.registerCorsConfiguration("/**",
                buildCorsConfig(gatewayProperties.getCors().getAllowedOrigins()));
        return source;
    }

    private CorsConfiguration buildCorsConfig(String commaSeparatedOrigins) {
        List<String> origins = Arrays.stream(commaSeparatedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    /**
     * Performs the resolve public paths operation.
     *
     * @return the resulting string instance.
     */
    private String[] resolvePublicPaths() {
        // Seed with all configurable paths — nulls come from unset fields, empty strings from
        // profile overrides (e.g., irctc-simulator="" in prod). pathMatchers() throws
        // IllegalArgumentException on null or empty patterns, so we filter before returning.
        List<String> paths = new ArrayList<>(Arrays.asList(
                pathMatchersConfig.getBookingsHealth(),
                // Driver onboarding public endpoints
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
                pathMatchersConfig.getNmsInAppWebsocket(),
                pathMatchersConfig.getRiderOnboardingActiveSchemaPublic(),
                pathMatchersConfig.getRiderOnboardingSubmissionsPublic(),
                pathMatchersConfig.getRiderTrainSearchPublic(),
                pathMatchersConfig.getRiderTrainPublic(),
                pathMatchersConfig.getRiderCommercePublic(),
                pathMatchersConfig.getRiderConfigPublic(),
                pathMatchersConfig.getNmsCallbacks(),
                // Always-public gateway-level paths
                "/actuator", "/actuator/**",
                "/fallback/**",
                // OpenAPI docs / Swagger UI -- the gateway's own (bare, unprefixed) paths, plus
                // every downstream service's docs as proxied through its own gateway prefix (e.g.
                // /api/payment/v3/api-docs) -- AntPathMatcher's "*" matches exactly one path
                // segment, so this covers every /api/<service>/... route declared in RouteConfig
                // without needing one literal entry per service.
                "/v3/api-docs", "/v3/api-docs/**",
                "/swagger-ui.html", "/swagger-ui/**",
                "/api/*/v3/api-docs", "/api/*/v3/api-docs/**",
                "/api/*/swagger-ui.html", "/api/*/swagger-ui/**",
                "/api/rider/*/v3/api-docs", "/api/rider/*/v3/api-docs/**",
                "/api/rider/*/swagger-ui.html", "/api/rider/*/swagger-ui/**",
                // Javadoc (see RouteConfig's *-javadoc routes).
                "/javadoc", "/javadoc/**",
                "/api/*/javadoc", "/api/*/javadoc/**",
                "/api/rider/*/javadoc", "/api/rider/*/javadoc/**"
        ));

        // Guard actuator wildcard concatenation against a null field value
        String actuator = pathMatchersConfig.getActuator();
        if (StringUtils.hasText(actuator)) {
            paths.add(actuator);
            if (!actuator.endsWith("/**")) paths.add(actuator + "/**");
        }

        // Same expansion as ReactiveJwtAuthenticationFilter and PrivilegeBasedAccessFilter (exact + /**): that filter skips the
        // token on every path under a public prefix, so a sub-path missing here (e.g. /api/v1/config/public/support-contacts)
        // reached this check unauthenticated and was refused with 401 even for a signed-in caller.
        String[] result = paths.stream()
                .filter(StringUtils::hasText)
                .flatMap(path -> path.endsWith("/**") ? java.util.stream.Stream.of(path) : java.util.stream.Stream.of(path, path + "/**"))
                .distinct()
                .toArray(String[]::new);
        log.info("Gateway security: permitting {} public paths", result.length);
        return result;
    }
}
