package com.heuristiq.audit.config.security;

import static com.heuristiq.audit.config.security.SecurityConstants.*;
import com.heuristiq.audit.config.AuditSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/** Validates (never issues) bearer tokens minted by Rider_UAM. */
@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(AuditSecurityProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("railcab.audit.auth.jwt-secret must be set and at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws io.jsonwebtoken.JwtException if the token is expired, malformed or badly signed */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
