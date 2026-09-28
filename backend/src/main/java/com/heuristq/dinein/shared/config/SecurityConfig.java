package com.heuristq.dinein.shared.config;

import com.heuristq.dinein.shared.security.JsonSecurityErrorHandlers;
import com.heuristq.dinein.shared.security.TokenAuthenticationFilter;
import com.heuristq.dinein.shared.security.TokenAuthenticator;
import com.heuristq.dinein.shared.security.csrf.SpaCsrfTokenRequestHandler;
import com.heuristq.dinein.shared.security.ratelimit.RateLimitFilter;
import com.heuristq.dinein.shared.security.ratelimit.RateLimitProperties;
import com.heuristq.dinein.shared.security.ratelimit.RateLimitService;
import com.heuristq.dinein.shared.web.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String V1 = ApiPaths.V1;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TokenAuthenticator tokenAuthenticator,
                                           RateLimitService rateLimitService,
                                           RateLimitProperties rateLimitProperties,
                                           JsonSecurityErrorHandlers errorHandlers) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(c -> c.path("/").sameSite("Lax"));

        http
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF only protects endpoints authenticated by cookies (refresh token, guest session).
                // Bearer-token endpoints are immune to CSRF, and the webhook is signature-verified.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .requireCsrfProtectionMatcher(cookieAuthenticatedMutations()))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers.entryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers(V1 + "/public/**", V1 + "/images/**", V1 + "/webhooks/**").permitAll()
                        // Key-authenticated inside the controller, like the webhooks above; see PlatformOnboardingController.
                        .requestMatchers(V1 + "/platform/**").permitAll()
                        .requestMatchers(V1 + "/auth/login", V1 + "/auth/refresh", V1 + "/auth/logout",
                                V1 + "/auth/kitchen-device", V1 + "/auth/csrf").permitAll()
                        .requestMatchers(V1 + "/auth/**").authenticated()
                        .requestMatchers(V1 + "/admin/**").hasAnyRole("OWNER", "MANAGER")
                        .requestMatchers(V1 + "/kitchen/**").hasAnyRole("KITCHEN", "OWNER", "MANAGER")
                        .requestMatchers(V1 + "/waiter/**").hasAnyRole("WAITER", "OWNER", "MANAGER")
                        .anyRequest().denyAll())
                .addFilterBefore(new TokenAuthenticationFilter(tokenAuthenticator), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimitService, rateLimitProperties, errorHandlers),
                        TokenAuthenticationFilter.class)
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    private static RequestMatcher cookieAuthenticatedMutations() {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        RequestMatcher unsafeMethod = request -> !SAFE_METHODS.contains(request.getMethod());
        RequestMatcher cookiePaths = new OrRequestMatcher(
                path.matcher(V1 + "/auth/refresh"),
                path.matcher(V1 + "/auth/logout"),
                path.matcher(V1 + "/public/**"));
        // Provider redirects (e.g. PayU surl/furl) are cross-site form posts authenticated by the provider's hash.
        RequestMatcher providerCallbacks = path.matcher(V1 + "/public/payments/{provider}/callback");
        return new AndRequestMatcher(unsafeMethod, cookiePaths, new NegatedRequestMatcher(providerCallbacks));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of() : properties.cors().allowedOrigins();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-XSRF-TOKEN",
                "If-None-Match", "X-Trace-Id"));
        config.setExposedHeaders(List.of("ETag", "X-Trace-Id", "Retry-After", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(V1 + "/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Disables Boot's generated default user; staff authentication is handled by {@code AuthService}. */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
