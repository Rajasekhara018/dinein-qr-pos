package com.heuristq.dinein.shared.security.jwt;

import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies short-lived HMAC-SHA256 access tokens for staff users. */
@Service
public class JwtTokenService {

    private static final String ISSUER = "dinein";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "usr";
    private static final String CLAIM_PWD_CHANGE = "pwc";
    private static final String CLAIM_RESTAURANT = "rid";

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtTokenService(AppProperties properties, Clock clock) {
        byte[] keyBytes = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = properties.security().accessTokenTtl();
        this.clock = clock;
    }

    public String issueAccessToken(StaffUserEntity user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_PWD_CHANGE, user.isMustChangePassword())
                .claim(CLAIM_RESTAURANT, user.getRestaurantId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public Optional<StaffPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new StaffPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    StaffRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.get(CLAIM_RESTAURANT, Long.class),
                    null,
                    Boolean.TRUE.equals(claims.get(CLAIM_PWD_CHANGE, Boolean.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
