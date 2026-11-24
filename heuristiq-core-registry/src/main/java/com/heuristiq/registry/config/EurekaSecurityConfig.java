package com.heuristiq.registry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Without this, the Eureka dashboard and the registration/discovery REST API are both wide open
 * to anyone who can reach port 9100 -- letting an attacker view every registered service instance
 * or register a fake one (spoofing/hijacking traffic meant for a real service). HTTP Basic over
 * the whole app (dashboard + /eureka/**) via a single shared credential every client service also
 * presents in its own eureka.client.service-url.defaultZone.
 */
@Configuration
public class EurekaSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Eureka clients (registration/renewal/cancel) are stateless HTTP Basic callers,
                // not browser sessions -- they never carry a CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers(RegistrySecurityConstants.EUREKA_API_PATHS))
                .authorizeHttpRequests(auth -> auth
                        // Broadened from just /actuator/health so Prometheus/OTel-collector
                        // scraping doesn't need the Eureka dashboard credentials -- a scraper
                        // isn't a logged-in admin (matches NMS's NmsSecurityConfig reasoning).
                        .requestMatchers(RegistrySecurityConstants.ACTUATOR_PATHS).permitAll()
                        // Javadoc (static, packaged into the jar) -- public like the other services.
                        .requestMatchers(RegistrySecurityConstants.JAVADOC_PATH, RegistrySecurityConstants.JAVADOC_SUBPATHS).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
