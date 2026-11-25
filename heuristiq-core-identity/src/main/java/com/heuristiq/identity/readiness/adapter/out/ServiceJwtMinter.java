package com.heuristiq.identity.readiness.adapter.out;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints the short-lived backend SERVICE token some services require on their internal endpoints (Rider_OnboardingService's
 * {@code /api/v1/internal/**} accepts only a bearer JWT with {@code role=SERVICE}, signed with the shared JWT secret, the
 * same convention Rider_WorkflowService uses). A fresh token per call, so a long-running UAM never sends an expired one.
 */
public class ServiceJwtMinter {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    /**
     * @param secret the shared JWT secret ({@code railcab.uam.auth.jwt-secret})
     * @param ttl    lifetime of each token
     * @param clock  time source
     */
    public ServiceJwtMinter(String secret, Duration ttl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
    }

    /** A new SERVICE token. */
    public String mint() {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(ReadinessAdapterConstants.SERVICE_SUBJECT)
                .claim(ReadinessAdapterConstants.ROLE_CLAIM, ReadinessAdapterConstants.SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
