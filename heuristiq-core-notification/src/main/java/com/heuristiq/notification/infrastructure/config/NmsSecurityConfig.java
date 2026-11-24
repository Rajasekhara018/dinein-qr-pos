package com.railcab.nms.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the NMS.
 *
 * Security is switchable for local development, but production deployments
 * should enable nms.security.enabled and provide X-NMS-API-Key or a JWT.
 */
@Configuration
public class NmsSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            NmsProperties props,
            NmsAuthenticationFilter nmsAuthenticationFilter,
            NmsInternalServiceAuthFilter nmsInternalServiceAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Service-to-service endpoints always need a service credential, even when user security is switched off.
            .addFilterBefore(nmsInternalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (!props.getSecurity().isEnabled()) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(NmsConfigConstants.INTERNAL_PATTERN).hasAuthority(NmsConfigConstants.SERVICE_AUTHORITY)
                    .anyRequest().permitAll());
            return http.build();
        }

        http
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> {
                // Broadened from /actuator/health/** so Prometheus/metrics scraping doesn't need
                // app-level auth -- a scraper isn't a logged-in admin.
                auth.requestMatchers(NmsConfigConstants.ACTUATOR_PATTERN).permitAll();
                // Browsers can't attach an Authorization header to a WS/SockJS handshake —
                // real auth for this endpoint happens inside WebSocketAuthChannelInterceptor,
                // at the STOMP CONNECT frame level, not here.
                auth.requestMatchers(NmsConfigConstants.IN_APP_WS_PATTERN).permitAll();
                // Provider delivery receipts carry no user or API-key credential; each callback
                // endpoint authenticates itself (shared secret or provider signature, fail-closed).
                auth.requestMatchers(NmsConfigConstants.CALLBACKS_PATTERN).permitAll();
                // Javadoc (static, packaged into the jar).
                auth.requestMatchers(NmsConfigConstants.JAVADOC_PATH, NmsConfigConstants.JAVADOC_PATTERN).permitAll();
                if (props.getSecurity().isExposeDocs()) {
                    auth.requestMatchers(
                            NmsConfigConstants.API_DOCS_PATTERN,
                            NmsConfigConstants.SWAGGER_UI_PATTERN,
                            NmsConfigConstants.SWAGGER_UI_HTML).permitAll();
                }
                // Service-to-service endpoints: only the authority NmsInternalServiceAuthFilter grants.
                auth.requestMatchers(NmsConfigConstants.INTERNAL_PATTERN).hasAuthority(NmsConfigConstants.SERVICE_AUTHORITY);
                // Who may manage templates, events, broadcasts and logs is decided by the API gateway's
                // privilege check (privilege.json: NMS_TEMPLATE_*, NMS_EVENT_*, NMS_BROADCAST_*, NMS_LOG_*);
                // here a caller only has to be authenticated. There are no role names in this service.
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(nmsAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
