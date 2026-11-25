package com.heuristiq.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * A separate filter chain for {@value SecurityConstants#INTERNAL_PATH}: only a caller holding the shared service
 * token gets in, and end-user JWTs are not accepted there. The main chain in {@link SecurityConfig} is left untouched.
 */
@Configuration
public class InternalServiceSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http,
                                                          ObjectMapper objectMapper,
                                                          @Value("${rider.internal.service-token:}") String serviceToken)
            throws Exception {
        return http
                .securityMatcher(SecurityConstants.INTERNAL_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Map.of(SecurityConstants.ERROR_FIELD,
                                    SecurityConstants.SERVICE_AUTHENTICATION_REQUIRED_MESSAGE));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Map.of(SecurityConstants.ERROR_FIELD,
                                    SecurityConstants.INTERNAL_CREDENTIAL_REQUIRED_MESSAGE));
                        }))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(SecurityConstants.SERVICE_AUTHORITY))
                .addFilterBefore(new InternalServiceAuthenticationFilter(serviceToken), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
