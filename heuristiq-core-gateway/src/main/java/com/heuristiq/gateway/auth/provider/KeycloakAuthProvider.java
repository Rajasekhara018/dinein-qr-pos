package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.auth.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Keycloak-backed auth provider — selected when
 * {@code railcab.gateway.identity-provider.type=keycloak}, resolved at runtime by
 * {@link AuthProviderRouter}. The bean is always registered; it is simply unused when a
 * different provider type is configured.
 *
 * <p>Enable with: {@code railcab.gateway.identity-provider.type=keycloak}
 *
 * <p>Introspection results are cached for {@code AuthConstants.KEYCLOAK_CACHE_TTL_SECONDS} to avoid a round-trip on
 * every request. Trade-off: a revoked token may be accepted for up to that window. Tune via
 * {@code keycloak.introspection-cache-ttl-seconds} if tighter revocation is needed.
 *
 * <p>Role mapping: Keycloak realm role names are normalised (lower-case, hyphens→underscores)
 * and looked up in {@link AuthConstants#KEYCLOAK_ROLE_MAP}. Add entries there if Keycloak realm role names diverge
 * from the defaults.
 */
@Slf4j
@Component
public class KeycloakAuthProvider implements IAuthProvider {

    @Value("${keycloak.server-url:http://localhost:8180}")
    private String serverUrl;

    @Value("${keycloak.realm:railcab}")
    private String realm;

    @Value("${keycloak.client-id:railcab-gateway}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Keyed by token — avoids one Keycloak round-trip per request on the valid-token path
    private final Cache<String, Map<String, Object>> introspectionCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(AuthConstants.KEYCLOAK_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                    .maximumSize(AuthConstants.INTROSPECTION_CACHE_MAX_SIZE)
                    .build();

    public KeycloakAuthProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public AuthProviderType providerType() {
        return AuthProviderType.KEYCLOAK;
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Map<String, Object> result = cachedIntrospect(token);
            return result != null && Boolean.TRUE.equals(result.get("active"));
        } catch (Exception e) {
            log.error("Keycloak introspection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses the {@code exp} claim locally (no network call) to distinguish expired from
     * otherwise-invalid tokens, giving callers a more informative error message.
     */
    @Override
    public boolean isTokenExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return true;
            String payload = parts[1];
            // Pad to multiple of 4 for Base64 decoder
            payload = payload + "=".repeat((4 - payload.length() % 4) % 4);
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(decoded, Map.class);
            Object exp = claims.get("exp");
            if (exp == null) return false;
            return Instant.now().getEpochSecond() > ((Number) exp).longValue();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String extractUserId(String token) {
        try {
            Map<String, Object> result = cachedIntrospect(token);
            if (result == null) return null;
            Object sub = result.get("sub");
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String extractRole(String token) {
        try {
            Map<String, Object> result = cachedIntrospect(token);
            return extractRolesFromIntrospection(result).stream().findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Every realm role the token holds, not just the first -- a user with more than one
     *  Keycloak realm role must be granted every role's privileges. */
    @Override
    public List<String> extractRoles(String token) {
        try {
            return extractRolesFromIntrospection(cachedIntrospect(token));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public UsernamePasswordAuthenticationToken toAuthentication(String token) {
        Map<String, Object> result = cachedIntrospect(token);
        if (result == null) {
            return new UsernamePasswordAuthenticationToken(null, null, List.of());
        }
        Object sub = result.get("sub");
        String userId = sub != null ? sub.toString() : null;
        List<SimpleGrantedAuthority> authorities = extractRolesFromIntrospection(result).stream()
                .map(role -> new SimpleGrantedAuthority(GatewayConstants.ROLE_PREFIX + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    @Override
    public boolean isExternallyManagedToken(String token) {
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromIntrospection(Map<String, Object> introspection) {
        if (introspection == null) return List.of();
        Map<String, Object> realmAccess = (Map<String, Object>) introspection.get("realm_access");
        if (realmAccess == null) return List.of();
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) return List.of();
        return roles.stream()
                .filter(r -> !r.startsWith("default-roles"))
                .map(this::mapRole)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Normalises a Keycloak role name to a RailCab role name. Roles are dynamic now (created via
     * UAM's RoleController, not a fixed enum), so there's nothing to validate against beyond
     * {@link AuthConstants#KEYCLOAK_ROLE_MAP} -- an unmapped role is simply forwarded uppercased as-is and left to
     * whatever downstream check (RolePrivilegeRepository) actually knows whether it's real.
     */
    private String mapRole(String keycloakRole) {
        if (keycloakRole == null) return null;
        String normalized = keycloakRole.toLowerCase().replace("-", "_").replace(" ", "_");
        String mapped = AuthConstants.KEYCLOAK_ROLE_MAP.get(normalized);
        if (mapped != null) return mapped;
        String upper = keycloakRole.toUpperCase().replace("-", "_");
        log.debug("Keycloak role '{}' has no ROLE_MAP entry — forwarding uppercased as-is", keycloakRole);
        return upper;
    }

    private Map<String, Object> cachedIntrospect(String token) {
        return introspectionCache.get(token, this::introspectRemote);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> introspectRemote(String token) {
        String introspectUrl = serverUrl + "/realms/" + realm
                + "/protocol/openid-connect/token/introspect";
        return webClient.post()
                .uri(introspectUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("token=" + token + "&client_id=" + clientId + "&client_secret=" + clientSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .block(AuthConstants.INTROSPECTION_TIMEOUT);
    }
}
