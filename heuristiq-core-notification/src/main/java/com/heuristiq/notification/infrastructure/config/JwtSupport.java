package com.heuristiq.notification.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Shared JWT verification — used by both {@link NmsAuthenticationFilter} (HTTP requests) and
 * {@link WebSocketAuthChannelInterceptor} (STOMP CONNECT frames), which have no header path
 * in common (HTTP header vs. STOMP native header) but identical token-verification logic.
 */
@Component
public class JwtSupport {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    /** The verified token: subject, roles and the organization ({@code tenantId} claim) of the session; tenantId is null when absent. */
    public record JwtPrincipal(String userId, List<String> roles, String tenantId) {

        /** True for a backend service token (role SERVICE) as opposed to a person's token. */
        public boolean isServiceToken() {
            return roles.contains(NmsConfigConstants.SERVICE_ROLE);
        }
    }

    /** Accepts the full header value (e.g. "Bearer &lt;token&gt;"); empty if missing/invalid/unparseable. */
    public Optional<JwtPrincipal> parseBearer(String headerValue) {
        if (!StringUtils.hasText(headerValue) || !headerValue.startsWith(NmsConfigConstants.BEARER_PREFIX)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(jwtSecret)) {
            return Optional.empty();
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(headerValue.substring(NmsConfigConstants.BEARER_PREFIX.length()))
                    .getPayload();
            String subject = claims.getSubject();
            return Optional.of(new JwtPrincipal(
                    StringUtils.hasText(subject) ? subject : NmsConfigConstants.DEFAULT_JWT_SUBJECT,
                    rolesFromClaims(claims),
                    tenantFromClaims(claims)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String tenantFromClaims(Claims claims) {
        Object tenant = claims.get(NmsConfigConstants.CLAIM_TENANT_ID);
        return tenant instanceof String value && StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> rolesFromClaims(Claims claims) {
        List<String> roles = new ArrayList<>();
        addRoles(roles, claims.get(NmsConfigConstants.CLAIM_ROLES));
        addRoles(roles, claims.get(NmsConfigConstants.CLAIM_AUTHORITIES));
        addRoles(roles, claims.get(NmsConfigConstants.CLAIM_SCOPE));
        addRoles(roles, claims.get(NmsConfigConstants.CLAIM_SCP));
        if (roles.isEmpty()) {
            roles.add(NmsConfigConstants.DEFAULT_CLIENT_ROLE);
        }
        return roles;
    }

    private void addRoles(List<String> roles, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> addRole(roles, String.valueOf(value)));
            return;
        }
        if (claim instanceof String value) {
            for (String role : value.split(NmsConfigConstants.ROLE_CLAIM_SEPARATOR_REGEX)) {
                addRole(roles, role);
            }
        }
    }

    private void addRole(List<String> roles, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String role = value.trim().toUpperCase(Locale.ROOT);
        if (role.startsWith(NmsConfigConstants.ROLE_PREFIX)) {
            role = role.substring(NmsConfigConstants.ROLE_PREFIX.length());
        }
        if (!roles.contains(role)) {
            roles.add(role);
        }
    }
}
