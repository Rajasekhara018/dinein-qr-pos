package com.heuristq.dinein.guest;

import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.security.CookieFactory;
import com.heuristq.dinein.shared.util.Hmac;
import com.heuristq.dinein.shared.util.SecureTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Stateless signed guest sessions: {@code base64url(sessionId|tableId|expiry).base64url(HMAC-SHA256)}.
 * No server-side storage is needed; orders are bound to the session id.
 */
@Service
public class GuestSessionService {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final String secret;
    private final Duration ttl;
    private final Clock clock;

    public GuestSessionService(AppProperties properties, Clock clock) {
        this.secret = properties.security().guestSessionSecret();
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("APP_GUEST_SESSION_SECRET must be at least 32 bytes");
        }
        this.ttl = properties.security().guestSessionTtl();
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    public GuestSession newSession(Long tableId) {
        return new GuestSession(SecureTokens.randomUrlSafe(16), tableId, clock.instant().plus(ttl));
    }

    /** Keeps the same session id (so "My orders" survives a re-scan) but extends the expiry. */
    public GuestSession renew(GuestSession existing, Long tableId) {
        return new GuestSession(existing.sessionId(), tableId, clock.instant().plus(ttl));
    }

    public String encode(GuestSession session) {
        String payload = session.sessionId() + "|" + session.tableId() + "|" + session.expiresAt().getEpochSecond();
        String body = ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + ENC.encodeToString(Hmac.sha256(body.getBytes(StandardCharsets.UTF_8), secret));
    }

    public Optional<GuestSession> decode(String token) {
        if (token == null || token.length() > 512) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String body = token.substring(0, dot);
        try {
            byte[] expected = Hmac.sha256(body.getBytes(StandardCharsets.UTF_8), secret);
            byte[] actual = DEC.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            String[] parts = new String(DEC.decode(body), StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 3) {
                return Optional.empty();
            }
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
            if (!expiresAt.isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new GuestSession(parts[0], Long.valueOf(parts[1]), expiresAt));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<GuestSession> fromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (CookieFactory.GUEST_COOKIE.equals(cookie.getName())) {
                return decode(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
