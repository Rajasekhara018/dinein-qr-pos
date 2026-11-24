package com.railcab.audit.application;

import com.railcab.audit.domain.AppChannel;
import com.railcab.audit.domain.AuditException;
import com.railcab.audit.domain.PageResult;
import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventFilter;
import com.railcab.audit.domain.AuditFacets;
import com.railcab.audit.domain.AuditPayload;
import com.railcab.audit.domain.AuditEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditIngestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    /** In-memory store that behaves like the real adapters for insert idempotency. */
    static final class FakeRepository implements AuditEventRepository {
        final Map<String, AuditEvent> events = new LinkedHashMap<>();
        final Map<String, AuditPayload> payloads = new HashMap<>();

        @Override
        public List<String> insertNew(List<AuditEvent> toStore) {
            List<String> inserted = new ArrayList<>();
            for (AuditEvent e : toStore) {
                if (events.putIfAbsent(e.getEventId(), e) == null) {
                    inserted.add(e.getEventId());
                }
            }
            return inserted;
        }

        @Override
        public void insertPayloads(List<AuditPayload> toStore) {
            toStore.forEach(p -> payloads.putIfAbsent(p.getEventId(), p));
        }

        @Override
        public Optional<AuditPayload> findPayload(String eventId) {
            return Optional.ofNullable(payloads.get(eventId));
        }

        @Override
        public Map<String, AuditPayload> findPayloads(Collection<String> eventIds) {
            return payloads;
        }

        @Override
        public Optional<AuditEvent> findById(long id) {
            return Optional.empty();
        }

        @Override
        public PageResult<AuditEvent> search(AuditEventFilter filter, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditEvent> searchForExport(AuditEventFilter filter, int max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditEvent> findByCorrelationId(String correlationId, int max, com.railcab.audit.domain.TenantScope scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuditFacets facets(Instant since, int maxValues, com.railcab.audit.domain.TenantScope scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long deleteExpired(Instant now) {
            return 0;
        }
    }

    private final AuditProperties props = new AuditProperties();
    private final FakeRepository repo = new FakeRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AuditIngestService service = new AuditIngestService(props, new EventSanitizer(props),
            new RetentionPolicy(props), repo, new SlidingWindowRateLimiter(clock), new PayloadSanitizer(props), clock);

    @Test
    void validEventsAreStoredWithRetentionExpiryPerClass() {
        IngestResult r = service.ingestInternal(List.of(
                InputBuilder.access().eventId("evt-read-0001").method("GET").build(),
                InputBuilder.access().eventId("evt-write-001").method("POST").build(),
                InputBuilder.access().eventId("evt-comm-0001").eventType("COMMUNICATION").method(null).path(null)
                        .statusCode(null).outcome("SUCCESS").build()));
        assertThat(r.accepted()).isEqualTo(3);
        assertThat(repo.events.get("evt-read-0001").getExpireAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(repo.events.get("evt-write-001").getExpireAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
        assertThat(repo.events.get("evt-comm-0001").getExpireAt()).isEqualTo(NOW.plus(Duration.ofDays(365)));
    }

    @Test
    void resendingABatchStoresEachEventOnce() {
        List<AuditEventInput> batch = List.of(InputBuilder.access().eventId("evt-same-0001").build(),
                InputBuilder.access().eventId("evt-same-0001").build(), InputBuilder.access().eventId("evt-other-001").build());
        IngestResult first = service.ingestInternal(batch);
        assertThat(first.accepted()).isEqualTo(2);
        assertThat(first.duplicates()).isEqualTo(1);
        IngestResult second = service.ingestInternal(batch);
        assertThat(second.accepted()).isZero();
        assertThat(second.duplicates()).isEqualTo(3);
        assertThat(repo.events).hasSize(2);
    }

    @Test
    void oneBadEventDoesNotFailTheBatch() {
        IngestResult r = service.ingestInternal(List.of(InputBuilder.access().build(),
                InputBuilder.access().eventType("BOGUS").build()));
        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(r.rejections().get(0).index()).isEqualTo(1);
    }

    @Test
    void batchSizeLimitsAreEnforced() {
        assertThatThrownBy(() -> service.ingestInternal(List.of())).isInstanceOf(AuditException.class);
        assertThatThrownBy(() -> service.ingestInternal(null)).isInstanceOf(AuditException.class);
        List<AuditEventInput> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            big.add(InputBuilder.access().build());
        }
        assertThatThrownBy(() -> service.ingestInternal(big)).isInstanceOf(AuditException.class)
                .hasMessageContaining("500");
        assertThat(service.ingestInternal(big.subList(0, 500)).accepted()).isEqualTo(500);
    }

    @Test
    void payloadIsStoredOnlyForNewEventsAndRedacted() {
        AuditEventInput in = InputBuilder.access().eventId("evt-payl-0001").method("POST")
                .requestHeaders(Map.of("Authorization", "Bearer abcdefghijkl")).requestBody("{\"pin\":\"1234\"}")
                .bodyContentType("application/json").build();
        service.ingestInternal(List.of(in));
        AuditPayload stored = repo.payloads.get("evt-payl-0001");
        assertThat(stored.getRequestHeaders()).containsEntry("Authorization", "***");
        assertThat(stored.getRequestBody()).contains("\"pin\":\"***\"");
        repo.payloads.clear();
        service.ingestInternal(List.of(in));
        assertThat(repo.payloads).as("duplicate event must not re-store its payload").isEmpty();
    }

    @Test
    void screenViewsTakeIdentityFromTheCallerNotTheBody() {
        CallerIdentity user = new CallerIdentity("real-user", "CUSTOMER", List.of("CUSTOMER"));
        ClientInfo client = new ClientInfo("corr-9", "req-9", "198.51.100.7", "Dart/3.4");
        ScreenViewInput view = new ScreenViewInput("sv-event-0001", "home.dashboard", null, "CUSTOMER_APP", "home", 900L,
                Map.of("appVersion", "1.2.3", "password", "x", "other", "y"));
        IngestResult r = service.ingestScreenViews(user, List.of(view), client);
        assertThat(r.accepted()).isEqualTo(1);
        AuditEvent e = repo.events.get("sv-event-0001");
        assertThat(e.getUserId()).isEqualTo("real-user");
        assertThat(e.getUserType()).isEqualTo("CUSTOMER");
        assertThat(e.getRole()).isEqualTo("CUSTOMER");
        assertThat(e.getSourceService()).isEqualTo("client-app");
        assertThat(e.getCorrelationId()).isEqualTo("corr-9");
        assertThat(e.getIpAddress()).isEqualTo("198.51.100.0");
        assertThat(e.getDetails()).containsOnlyKeys("appVersion");
        assertThat(repo.payloads).isEmpty();
    }

    @Test
    void screenViewsRejectBadKeysAndChannels() {
        CallerIdentity user = new CallerIdentity("u", "STAFF_X", List.of("STAFF_X"));
        ClientInfo client = new ClientInfo(null, null, null, null);
        IngestResult r = service.ingestScreenViews(user, List.of(
                new ScreenViewInput(null, "Bad Key", null, "WEB", null, null, null),
                new ScreenViewInput(null, "ok.key", null, "SERVICE", null, null, null),
                new ScreenViewInput(null, "ok.key", null, "ADMIN_WEB", null, null, null)), client);
        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.rejected()).isEqualTo(2);
    }

    @Test
    void screenViewsFromTheWalletWebAppAreAcceptedAndUnknownChannelsRejected() {
        CallerIdentity user = new CallerIdentity("agent-1", "AGENT", List.of("AGENT"));
        ClientInfo client = new ClientInfo(null, null, null, null);
        IngestResult r = service.ingestScreenViews(user, List.of(
                new ScreenViewInput("sv-wallet-0001", "agent_wallet.dashboard", null, "wallet_web", "agent_wallet", null, null),
                new ScreenViewInput("sv-wallet-0002", "agent_wallet.dashboard", null, "WALLET_APP", "agent_wallet", null, null)),
                client);
        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(repo.events.get("sv-wallet-0001").getAppChannel()).isEqualTo(AppChannel.WALLET_WEB);
        assertThat(repo.events).doesNotContainKey("sv-wallet-0002");
    }

    @Test
    void screenViewsAreRateLimitedPerUser() {
        props.getIngest().setScreenViewsPerMinute(60);
        CallerIdentity user = new CallerIdentity("busy", "CUSTOMER", List.of("CUSTOMER"));
        ClientInfo client = new ClientInfo(null, null, null, null);
        assertThat(service.ingestScreenViews(user, views(50), client).dropped()).isZero();
        IngestResult second = service.ingestScreenViews(user, views(20), client);
        assertThat(second.accepted()).isEqualTo(10);
        assertThat(second.dropped()).isEqualTo(10);
        assertThat(service.ingestScreenViews(user, views(5), client).dropped()).isEqualTo(5);
        CallerIdentity other = new CallerIdentity("calm", "CUSTOMER", List.of("CUSTOMER"));
        assertThat(service.ingestScreenViews(other, views(5), client).accepted()).isEqualTo(5);
        assertThatThrownBy(() -> service.ingestScreenViews(user, views(51), client)).isInstanceOf(AuditException.class);
    }

    private static List<ScreenViewInput> views(int n) {
        List<ScreenViewInput> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ScreenViewInput(null, "home.dashboard", null, "WEB", null, null, null));
        }
        return out;
    }
}
