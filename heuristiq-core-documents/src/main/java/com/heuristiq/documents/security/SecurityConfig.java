package com.heuristiq.documents.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;
    private final InternalServiceAuthFilter internalServiceAuthFilter;

    public SecurityConfig(GatewayAuthFilter gatewayAuthFilter, InternalServiceAuthFilter internalServiceAuthFilter) {
        this.gatewayAuthFilter = gatewayAuthFilter;
        this.internalServiceAuthFilter = internalServiceAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // /documents/public/** now needs /api/v1 explicitly: with context-path removed in
                // favor of ApiVersionConfig, the servlet path is the full path. /actuator/** and
                // the docs paths are unchanged (never under the context-path's controller-mapping
                // side either).
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DmsSecurityConstants.ACTUATOR_PATTERN).permitAll()
                        .requestMatchers(DmsSecurityConstants.PUBLIC_DOWNLOAD_PATTERN).permitAll()
                        // Service-to-service only: needs the authority InternalServiceAuthFilter grants for a valid
                        // service credential. A gateway-authenticated user never has it.
                        .requestMatchers(DmsSecurityConstants.INTERNAL_PATH_PATTERN)
                                .hasAuthority(DmsSecurityConstants.SERVICE_AUTHORITY)
                        .requestMatchers(DmsSecurityConstants.API_DOCS_PATTERN, DmsSecurityConstants.SWAGGER_UI_HTML,
                                DmsSecurityConstants.SWAGGER_UI_PATTERN, DmsSecurityConstants.JAVADOC_PATH,
                                DmsSecurityConstants.JAVADOC_PATTERN).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
