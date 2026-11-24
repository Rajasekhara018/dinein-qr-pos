package com.heuristiq.paymentservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JWT bearer-token validation. This service only ever validates tokens
 * issued elsewhere (the Ride/Auth service) — it does not log users in — so no
 * token-generation endpoint is exposed here, only the validation machinery the
 * gateway/filter needs to authenticate incoming requests.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return validateAndExtract(token).getSubject();
    }

    public String extractRole(String token) {
        return validateAndExtract(token).get("role", String.class);
    }

    /** The organization of the session (the {@code tenantId} claim), or null when the token carries none. */
    public String extractTenantId(String token) {
        return validateAndExtract(token).get("tenantId", String.class);
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
