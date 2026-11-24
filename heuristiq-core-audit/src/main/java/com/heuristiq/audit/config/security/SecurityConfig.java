package com.heuristiq.audit.config.security;

import static com.heuristiq.audit.config.security.SecurityConstants.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security with two chains.
 *
 * <p><b>Internal</b> ({@code /api/v1/audit/internal/**}): service-to-service only, see
 * {@link ServiceAuthenticationFilter}. End-user tokens never reach it, and the gateway refuses to route to it.
 *
 * <p><b>Everything else</b>: actuator and API docs are open; the screen-view and query endpoints need a valid
 * UAM bearer token. There are deliberately no role checks in this service (no {@code @PreAuthorize}, no role
 * names): authorization of the query endpoints is privilege-based and enforced at the API gateway from
 * {@code privilege.json}; the screen-view endpoint is intentionally open to any signed-in user.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http, JwtService jwtService,
                                                          AuditAuthenticationEntryPoint entryPoint,
                                                          @Value("${rider.internal.service-token:}") String serviceToken)
            throws Exception {
        http.securityMatcher(INTERNAL_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((req, res, ex) -> entryPoint.commence(req, res, null)))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new ServiceAuthenticationFilter(serviceToken, jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, MeterRegistry meterRegistry,
                                                   AuditAuthenticationEntryPoint entryPoint) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ACTUATOR_PATTERN, ROOT_ACTUATOR_PATTERN).permitAll()
                        .requestMatchers(DOC_PATTERNS).permitAll()
                        .anyRequest().authenticated());
        http.addFilterBefore(new JwtAuthenticationFilter(jwtService, meterRegistry), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
