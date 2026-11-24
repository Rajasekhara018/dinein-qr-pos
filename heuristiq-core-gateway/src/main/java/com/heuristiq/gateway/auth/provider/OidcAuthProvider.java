package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.auth.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.heuristiq.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generic OIDC-backed auth provider — selected when
 * {@code railcab.gateway.identity-provider.type=oidc}, resolved at runtime by
 * {@link AuthProviderRouter}. The bean is always registered; it is simply unused when a
 * different provider type is configured.
 *
 * <p>Unlike {@link KeycloakAuthProvider}, which is wired to Keycloak's realm-shaped
 * introspection URL and its {@code realm_access.roles} claim, this provider is configured
 * entirely through {@code railcab.gateway.oidc.*} — introspection endpoint, client
 * credentials, and role claim/mapping — so it works against any RFC 7662-compatible
 * authorization server (Auth0, Okta, Azure AD, etc.) without a vendor-specific class.
 * Mirrors UAM's generic {@code OidcIdentityProviderAuthService}: one config-driven
 * implementation instead of one class per vendor.
 *
 * <p>Introspection results are cached per {@code railcab.gateway.oidc.introspection-cache-ttl-seconds}
 * to avoid a round-trip on every request — same trade-off as {@link KeycloakAuthProvider}.
 */
@Slf4j
@Component
public class OidcAuthProvider implements IAuthProvider {

    private final GatewayProperties.Oidc properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, Map<String, Object>> introspectionCache;

    public OidcAuthProvider(GatewayProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties.getOidc();
        this.webClient = webClientBuilder.build();
        this.introspectionCache = Caffeine.newBuilder()
                .expireAfterWrite(this.properties.getIntrospectionCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(AuthConstants.INTROSPECTION_CACHE_MAX_SIZE)
                .build();
    }

    @Override
    public AuthProviderType providerType() {
        return AuthProviderType.OIDC;
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Map<String, Object> result = cachedIntrospect(token);
            return result != null && Boolean.TRUE.equals(result.get("active"));
        } catch (Exception e) {
            log.error("OIDC introspection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses the {@code exp} claim locally (no network call) to distinguish expired from
     * otherwise-invalid tokens, giving callers a more informative error message. Only works
     * when the token is a JWT; opaque tokens fall through to introspection-only validation.
     */
    @Override
    public boolean isTokenExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return true;
            String payload = parts[1];
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
            Object sub = result.get(properties.getSubjectClaim());
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String extractRole(String token) {
        try {
            return extractRolesFromIntrospection(cachedIntrospect(token)).stream().findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Every role the token holds, not just the first -- a user with more than one role in the
     *  configured roles claim must be granted every role's privileges. */
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
        Object sub = result.get(properties.getSubjectClaim());
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
        Object raw = introspection.get(properties.getRolesClaim());
        List<String> roles;
        if (raw instanceof List<?> list) {
            roles = list.stream().map(String::valueOf).toList();
        } else if (raw instanceof String s && StringUtils.hasText(s)) {
            roles = Arrays.stream(s.split("[,\\s]+")).filter(StringUtils::hasText).toList();
        } else {
            return List.of();
        }
        return roles.stream()
                .map(this::mapRole)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Normalises a provider role name to a RailCab role name via the configurable role map. Roles
     * are dynamic now (created via UAM's RoleController, not a fixed enum), so there's nothing to
     * validate against beyond the map -- an unmapped role is simply forwarded uppercased as-is.
     */
    private String mapRole(String providerRole) {
        if (providerRole == null) return null;
        String normalized = providerRole.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        String mapped = properties.getRoleMap().get(normalized);
        if (mapped != null) return mapped;
        String upper = providerRole.toUpperCase(Locale.ROOT).replace("-", "_");
        log.debug("OIDC role '{}' has no mapping in railcab.gateway.oidc.role-map — forwarding uppercased as-is", providerRole);
        return upper;
    }

    private Map<String, Object> cachedIntrospect(String token) {
        return introspectionCache.get(token, this::introspectRemote);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> introspectRemote(String token) {
        if (!StringUtils.hasText(properties.getIntrospectionUri())) {
            log.error("railcab.gateway.oidc.introspection-uri is not configured");
            return null;
        }
        return webClient.post()
                .uri(properties.getIntrospectionUri())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("token=" + token
                        + "&client_id=" + properties.getClientId()
                        + "&client_secret=" + properties.getClientSecret())
                .retrieve()
                .bodyToMono(Map.class)
                .block(AuthConstants.INTROSPECTION_TIMEOUT);
    }
}
