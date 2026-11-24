package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.auth.AuthConstants;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default auth provider — selected when {@code railcab.gateway.identity-provider.type=hmac}
 * (the default), resolved at runtime by {@link AuthProviderRouter}.
 *
 * <p>Validates HMAC-SHA256 JWTs issued by AdminAPI and BookingsAPI. Each service issues
 * tokens with its own secret:
 * <ul>
 *   <li>{@code railcab.gateway.auth.jwt-secret} — AdminAPI tokens (SUPER_ADMIN, MANAGER, etc.)</li>
 *   <li>{@code railcab.gateway.auth.customer-jwt-secret} — BookingsAPI tokens (CUSTOMER, DRIVER)</li>
 * </ul>
 *
 * <p>Validation strategy: try the admin secret first. On a signature mismatch
 * ({@link SignatureException}), fall back to the customer secret. Any other failure
 * (malformed token, expiry) is surfaced immediately without the fallback attempt.
 *
 * <p>One request verifies and parses its token once ({@link #verify(String)}); the single-purpose methods below each
 * parse on their own and are kept for callers that need just one value. The verifying parser for each secret is built
 * once and reused while the configured secret stays the same.
 *
 * <p>To switch to Keycloak, set {@code railcab.gateway.identity-provider.type=keycloak} —
 * this bean stays registered, {@link AuthProviderRouter} just stops selecting it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmacJwtAuthProvider implements IAuthProvider {

    private final GatewayProperties properties;
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.gateway.auth.service.UamSessionPolicyClient sessionPolicies;

    /** Parser verifying with the admin secret, rebuilt only when the configured secret changes. */
    private final AtomicReference<SecretParser> adminParser = new AtomicReference<>();

    /** Parser verifying with the customer secret, rebuilt only when the configured secret changes. */
    private final AtomicReference<SecretParser> customerParser = new AtomicReference<>();

    @Override
    public AuthProviderType providerType() {
        return AuthProviderType.HMAC;
    }

    /**
     * Verifies and parses the token once and derives everything the gateway needs for this request from that one
     * parse. Same outcome as calling {@link #isTokenValid}, {@link #isTokenExpired}, {@link #toAuthentication} and
     * {@link #isExternallyManagedToken} one after another: a token that fails verification is always reported as
     * expired, because {@link #isTokenExpired} answers true for any token it cannot parse.
     */
    @Override
    public TokenVerification verify(String token) {
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (ExpiredJwtException e) {
            log.debug("Token expired for subject={}", e.getClaims().getSubject());
            return TokenVerification.rejected(true);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("HMAC token validation failed: {}", e.getMessage());
            return TokenVerification.rejected(true);
        }
        if (claims.getExpiration().before(new Date())) {
            return TokenVerification.rejected(true);
        }
        if ("MFA_ENROLLMENT".equals(claims.get("purpose"))) return TokenVerification.rejected(false);
        if (isUamIssued(claims) && sessionPolicies != null && (claims.get("sid") == null || !sessionPolicies.active(token)))
            return TokenVerification.rejected(false);
        return TokenVerification.accepted(authenticationOf(claims), isUamIssued(claims));
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            log.debug("Token expired for subject={}", e.getClaims().getSubject());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("HMAC token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            // Includes ExpiredJwtException: anything that cannot be parsed counts as expired.
            return true;
        }
    }

    @Override
    public String extractUserId(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Exception e) {
            log.debug("Could not extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String extractRole(String token) {
        try {
            return roleOf(parseClaims(token));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads UAM's {@code roles} claim (the full list a user was assigned) rather than just the
     * single {@code role} claim -- a caller with more than one UamRole must be granted every
     * role's privileges, not just the first. Falls back to {@code role} for tokens that only ever
     * carry the singular claim (e.g. older tokens, or AdminAPI/BookingsAPI-issued tokens that
     * don't set {@code roles}).
     */
    @Override
    public List<String> extractRoles(String token) {
        try {
            return rolesOf(parseClaims(token));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public UsernamePasswordAuthenticationToken toAuthentication(String token) {
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (Exception e) {
            log.debug("Could not extract userId from token: {}", e.getMessage());
            return new UsernamePasswordAuthenticationToken(null, null, List.of());
        }
        return authenticationOf(claims);
    }

    /** True when the token carries the {@code orgWide} claim (set by UAM for a role holding the organization-wide privilege). */
    public boolean extractOrgWide(String token) {
        try {
            return orgWideOf(parseClaims(token));
        } catch (Exception e) {
            return false;
        }
    }

    /** The organization the token's session is in, or null when the token carries none. */
    public String extractTenantId(String token) {
        try {
            return tenantIdOf(parseClaims(token));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isExternallyManagedToken(String token) {
        try {
            return isUamIssued(parseClaims(token));
        } catch (Exception e) {
            return false;
        }
    }

    private static UsernamePasswordAuthenticationToken authenticationOf(Claims claims) {
        List<SimpleGrantedAuthority> authorities = rolesOf(claims).stream()
                .map(role -> new SimpleGrantedAuthority(GatewayConstants.ROLE_PREFIX + role))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        String tenantId = tenantIdOf(claims);
        boolean orgWide = orgWideOf(claims);
        Object rawApps = claims.get("apps");
        List<String> applications = rawApps instanceof List<?> list ? list.stream()
                .filter(String.class::isInstance).map(String.class::cast).filter(value -> !value.isBlank()).toList() : List.of();
        if (tenantId != null || orgWide || !applications.isEmpty()) {
            authentication.setDetails(new CallerDetails(tenantId, orgWide, applications));
        }
        return authentication;
    }

    private static List<String> rolesOf(Claims claims) {
        Object roles = claims.get(AuthConstants.CLAIM_ROLES);
        if (roles instanceof List<?> list && !list.isEmpty()) {
            Set<String> distinct = new LinkedHashSet<>();
            list.forEach(r -> distinct.add(String.valueOf(r)));
            return List.copyOf(distinct);
        }
        String role = roleOf(claims);
        return role == null ? List.of() : List.of(role);
    }

    /** The singular {@code role} claim; null when absent or not a string. */
    private static String roleOf(Claims claims) {
        return claims.get(AuthConstants.CLAIM_ROLE) instanceof String role ? role : null;
    }

    private static String tenantIdOf(Claims claims) {
        Object tenant = claims.get(AuthConstants.CLAIM_TENANT_ID);
        return tenant == null || String.valueOf(tenant).isBlank() ? null : String.valueOf(tenant);
    }

    private static boolean orgWideOf(Claims claims) {
        return Boolean.parseBoolean(String.valueOf(claims.get(AuthConstants.CLAIM_ORG_WIDE)));
    }

    private static boolean isUamIssued(Claims claims) {
        return AuthConstants.UAM_TOKEN_ISSUER.equalsIgnoreCase(claims.getIssuer());
    }

    /**
     * Tries the admin secret first. Falls back to the customer secret only on a
     * {@link SignatureException} — meaning the token was structurally valid but signed
     * with a different key (i.e., it's a BookingsAPI token). Expiry and malformed
     * token errors propagate immediately without the fallback.
     */
    private Claims parseClaims(String token) {
        try {
            return parserFor(adminParser, properties.getAuth().getJwtSecret())
                    .parseSignedClaims(token).getPayload();
        } catch (SignatureException e) {
            log.debug("Admin key mismatch — trying customer key for token");
            return parserFor(customerParser, properties.getAuth().getCustomerJwtSecret())
                    .parseSignedClaims(token).getPayload();
        }
    }

    /**
     * Returns the cached parser for this secret, building it (and its key) only when there is none yet or the
     * configured secret has changed. A secret that cannot make a key fails here on every call, as before.
     */
    private static JwtParser parserFor(AtomicReference<SecretParser> cache, String secret) {
        SecretParser cached = cache.get();
        if (cached != null && cached.secret().equals(secret)) {
            return cached.parser();
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        JwtParser parser = Jwts.parser().verifyWith(key).build();
        cache.set(new SecretParser(secret, parser));
        return parser;
    }

    /** A verifying parser together with the secret it was built from. */
    private record SecretParser(String secret, JwtParser parser) {
    }
}
