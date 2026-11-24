package com.heuristiq.uam.security;

import com.heuristiq.uam.config.UamProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UamProperties properties,
                                                   ObjectProvider<JwtAuthenticationFilter> jwtFilter,
                                                   ObjectProvider<TenantContextFilter> tenantContextFilter,
                                                   Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // These matchers used to be unprefixed because server.servlet.context-path
                        // (/api/v1/uam) stripped that prefix before Spring Security ever saw the
                        // path. Versioning moved to a per-@RestController prefix instead (see
                        // com.heuristiq.uam.config.ApiVersionConfig's javadoc) — the incoming request
                        // path Security matches against now includes /api/v1/uam, so every matcher
                        // here must too, or these routes silently fall through to
                        // .anyRequest().authenticated() (breaking login/register/OTP and the docs
                        // UI, not just tightening them).
                        .requestMatchers(SecurityConstants.PUBLIC_AUTH_PATH, SecurityConstants.ACTUATOR_PATH).permitAll()
                        .requestMatchers(SecurityConstants.API_DOC_PATHS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated());

        if (usesExternalJwtValidation(properties)) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                    jwtAuthenticationConverter)));
            http.addFilterAfter(tenantContextFilter.getObject(), BearerTokenAuthenticationFilter.class);
            return http.build();
        }

        http.addFilterBefore(jwtFilter.getObject(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(tenantContextFilter.getObject(), JwtAuthenticationFilter.class);
        return http.build();
    }

@Bean
public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(UamProperties properties) {
    JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
    return new Converter<Jwt, AbstractAuthenticationToken>() {
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopeConverter.convert(jwt));
            addRoleAuthorities(authorities, jwt.getClaim(SecurityConstants.ROLE_CLAIM));
            addRoleAuthorities(authorities, jwt.getClaim(SecurityConstants.ROLES_CLAIM));
            addNestedRoles(authorities, jwt.getClaim(SecurityConstants.REALM_ACCESS_CLAIM));
            addResourceRoles(authorities, jwt.getClaim(SecurityConstants.RESOURCE_ACCESS_CLAIM), properties);
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        }
    };
}

    private void addRoleAuthorities(Set<GrantedAuthority> authorities, Object value) {
        if (value instanceof String role) {
            addRoleAuthority(authorities, role);
        } else if (value instanceof Collection<?> roles) {
            roles.forEach(role -> addRoleAuthority(authorities, String.valueOf(role)));
        }
    }

    @SuppressWarnings("unchecked")
    private void addNestedRoles(Set<GrantedAuthority> authorities, Object value) {
        if (value instanceof Map<?, ?> map) {
            addRoleAuthorities(authorities, map.get(SecurityConstants.ROLES_CLAIM));
        }
    }

    @SuppressWarnings("unchecked")
    private void addResourceRoles(Set<GrantedAuthority> authorities, Object value, UamProperties properties) {
        if (!(value instanceof Map<?, ?> resourceAccess) || properties == null) {
            return;
        }
        List<String> clientIds = List.of(
                properties.getKeycloak().getClientId(),
                properties.getOidc().getClientId());
        for (String clientId : clientIds) {
            Object clientAccess = resourceAccess.get(clientId);
            if (clientAccess instanceof Map<?, ?> clientAccessMap) {
                addRoleAuthorities(authorities, clientAccessMap.get(SecurityConstants.ROLES_CLAIM));
            }
        }
    }

    private void addRoleAuthority(Set<GrantedAuthority> authorities, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        String normalized = role.startsWith(SecurityConstants.ROLE_AUTHORITY_PREFIX)
                ? role : SecurityConstants.ROLE_AUTHORITY_PREFIX + role;
        authorities.add(new SimpleGrantedAuthority(normalized));
    }

    private boolean usesExternalJwtValidation(UamProperties properties) {
        return properties.getIdentityProvider().isEnabled()
                && SecurityConstants.JWT_TOKEN_VALIDATION.equalsIgnoreCase(properties.getIdentityProvider().getTokenValidation());
    }
}
