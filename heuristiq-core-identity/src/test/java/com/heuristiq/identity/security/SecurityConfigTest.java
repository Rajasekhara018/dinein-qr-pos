package com.heuristiq.identity.security;

import com.heuristiq.identity.config.UamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    private UamProperties properties;
    private Converter<Jwt, AbstractAuthenticationToken> converter;

    @BeforeEach
    void setUp() {
        properties = new UamProperties();
        properties.getKeycloak().setClientId("kc-client");
        properties.getOidc().setClientId("oidc-client");
        converter = new SecurityConfig().jwtAuthenticationConverter(properties);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
                .issuedAt(Instant.parse("2025-01-01T00:00:00Z")).expiresAt(Instant.parse("2125-01-01T00:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private List<String> authorities(Map<String, Object> claims) {
        return converter.convert(jwt(claims)).getAuthorities().stream().map(Object::toString).toList();
    }

    @Test
    void principalNameIsTheSubject() {
        assertThat(converter.convert(jwt(Map.of("x", "y"))).getName()).isEqualTo("sub-1");
    }

    @Test
    void scopesBecomeScopeAuthorities() {
        assertThat(authorities(Map.of("scope", "read write"))).contains("SCOPE_read", "SCOPE_write");
    }

    @Test
    void singleRoleClaimIsPrefixedOnce() {
        assertThat(authorities(Map.of("role", "ADMIN"))).contains("ROLE_ADMIN");
        assertThat(authorities(Map.of("role", "ROLE_OPS"))).contains("ROLE_OPS").doesNotContain("ROLE_ROLE_OPS");
    }

    @Test
    void rolesListSkipsBlankEntries() {
        assertThat(authorities(Map.of("roles", List.of("A", "ROLE_B", " ")))).contains("ROLE_A", "ROLE_B")
                .noneMatch(a -> a.equals("ROLE_ "));
    }

    @Test
    void keycloakRealmRolesAreRead() {
        assertThat(authorities(Map.of("realm_access", Map.of("roles", List.of("REALM_ADMIN"))))).contains("ROLE_REALM_ADMIN");
        assertThat(authorities(Map.of("realm_access", "not-a-map"))).noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    void resourceRolesAreReadOnlyForConfiguredClients() {
        Map<String, Object> resourceAccess = Map.of(
                "kc-client", Map.of("roles", List.of("KC_ROLE")),
                "oidc-client", Map.of("roles", "OIDC_ROLE"),
                "other-client", Map.of("roles", List.of("FOREIGN")));

        List<String> granted = authorities(Map.of("resource_access", resourceAccess));

        assertThat(granted).contains("ROLE_KC_ROLE", "ROLE_OIDC_ROLE").doesNotContain("ROLE_FOREIGN");
        assertThat(authorities(Map.of("resource_access", "junk"))).noneMatch(a -> a.startsWith("ROLE_"));
        assertThat(authorities(Map.of("resource_access", Map.of("kc-client", "junk")))).noneMatch(a -> a.startsWith("ROLE_"));
    }

    @Test
    void nullPropertiesIgnoreResourceRoles() {
        Converter<Jwt, AbstractAuthenticationToken> noProps = new SecurityConfig().jwtAuthenticationConverter(null);
        assertThat(noProps.convert(jwt(Map.of("resource_access", Map.of("kc-client", Map.of("roles", List.of("X"))))))
                .getAuthorities()).extracting(Object::toString).noneMatch(a -> a.equals("ROLE_X"));
    }
}
