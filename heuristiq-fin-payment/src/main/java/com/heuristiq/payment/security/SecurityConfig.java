package com.heuristiq.paymentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.paymentservice.shared.dto.ResponseObject;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final GatewayElevation gatewayElevation;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    @Value("${rider.internal.service-token:}")
    private String serviceToken;

    public SecurityConfig(JwtService jwtService, GatewayElevation gatewayElevation, ObjectMapper objectMapper,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtService = jwtService;
        this.gatewayElevation = gatewayElevation;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Service-to-service chain for /api/v1/internal/** (e.g. Rider_AdminAPI's payments view). Only
     * the shared X-Rider-Service-Token is accepted here — the JWT filter is deliberately NOT
     * installed on this chain, so an end-user bearer token (whatever its role claim) can never
     * reach these endpoints. Ordered ahead of the catch-all end-user chain below.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(SecurityConstants.INTERNAL_PATH_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ResponseObject.builder()
                                    .status(false)
                                    .errorMsg("Service authentication required.")
                                    .build());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ResponseObject.builder()
                                    .status(false)
                                    .errorMsg("Access denied. Service credential required.")
                                    .build());
                        })
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new ServiceAuthenticationFilter(serviceToken),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ResponseObject.builder()
                                    .status(false)
                                    .errorMsg("Authentication required. Please login.")
                                    .build());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ResponseObject.builder()
                                    .status(false)
                                    .errorMsg("Access denied. You do not have permission for this action.")
                                    .build());
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Actuator is not @RestController-annotated, so ApiVersionConfig's addPathPrefix
                        // doesn't touch it — it stays unprefixed regardless of API version.
                        // Swagger/OpenAPI docs (read through the gateway's central Swagger UI, no user JWT).
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/javadoc", "/javadoc/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Razorpay webhooks — Razorpay itself calls these with no user JWT; authenticity
                        // is established via HMAC signature verification inside each handler instead.
                        .requestMatchers(SecurityConstants.PAYMENT_WEBHOOK_PATH).permitAll()
                        .requestMatchers(SecurityConstants.WALLET_TOPUP_WEBHOOK_PATH).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, gatewayElevation),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
