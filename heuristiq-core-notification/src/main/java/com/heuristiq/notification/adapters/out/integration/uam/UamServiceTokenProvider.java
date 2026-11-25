package com.heuristiq.notification.adapters.out.integration.uam;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints a short-lived internal JWT for NMS-to-UAM calls (audience resolution for broadcasts —
 * see {@link UamUserClient}). UAM has no internal-API-key trust mechanism like NMS's own
 * {@code X-NMS-API-Key} (NmsAuthenticationFilter) -- it only accepts real JWTs -- so this signs
 * a synthetic service-account token with the same shared HMAC secret ({@code jwt.secret}, which
 * must match UAM's {@code railcab.uam.auth.jwt-secret}) that UAM's own JwtAuthenticationFilter
 * already verifies signature-only, without a DB-backed user lookup, for any caller.
 */
@Component
public class UamServiceTokenProvider {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    public String mintServiceToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UamClientConstants.SERVICE_SUBJECT)
                .claim(UamClientConstants.CLAIM_ROLES, UamClientConstants.SERVICE_TOKEN_ROLES)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(UamClientConstants.TOKEN_TTL)))
                .signWith(key)
                .compact();
    }
}
