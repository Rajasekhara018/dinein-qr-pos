package com.heuristq.dinein.guest;

import com.heuristq.dinein.shared.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuestSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private static GuestSessionService service(Clock clock) {
        AppProperties.Security security = new AppProperties.Security("x".repeat(32), Duration.ofMinutes(15),
                Duration.ofDays(7), Duration.ofDays(30), "guest-secret-guest-secret-guest-secret", Duration.ofHours(12),
                5, Duration.ofMinutes(15), "owner", "pw", null);
        AppProperties props = new AppProperties("http://localhost", new AppProperties.Cors(List.of()),
                new AppProperties.Cookies(false), security, null, null, new AppProperties.Seed(false));
        return new GuestSessionService(props, clock);
    }

    @Test
    void roundTripsASignedSession() {
        GuestSessionService svc = service(Clock.fixed(NOW, ZoneOffset.UTC));
        GuestSession session = svc.newSession(7L);

        GuestSession decoded = svc.decode(svc.encode(session)).orElseThrow();

        assertThat(decoded.sessionId()).isEqualTo(session.sessionId());
        assertThat(decoded.tableId()).isEqualTo(7L);
    }

    @Test
    void rejectsTamperedPayload() {
        GuestSessionService svc = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = svc.encode(svc.newSession(7L));
        String signature = token.substring(token.indexOf('.'));
        String forgedBody = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("victimSession|7|" + NOW.plusSeconds(3600).getEpochSecond()).getBytes());

        assertThat(svc.decode(forgedBody + signature)).isEmpty();
        assertThat(svc.decode(token + "x")).isEmpty();
        assertThat(svc.decode("garbage")).isEmpty();
    }

    @Test
    void rejectsExpiredSession() {
        GuestSessionService issuer = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuer.encode(issuer.newSession(1L));

        GuestSessionService later = service(Clock.fixed(NOW.plus(Duration.ofHours(13)), ZoneOffset.UTC));

        assertThat(later.decode(token)).isEmpty();
    }
}
