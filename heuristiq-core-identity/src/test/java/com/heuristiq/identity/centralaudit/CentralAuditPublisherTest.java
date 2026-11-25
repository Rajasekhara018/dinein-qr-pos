package com.heuristiq.identity.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The audit outbox end to end on H2 with the real V28 migration: enqueue, drain, backoff, leases, bound and tenant id. */
class CentralAuditPublisherTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-02T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class RecordingSink implements CentralAuditSink {
        final List<List<CentralAuditEvent>> batches = new ArrayList<>();
        boolean failing;
        Runnable duringSend = () -> {
        };

        @Override
        public void send(List<CentralAuditEvent> events) {
            duringSend.run();
            if (failing) {
                throw new IllegalStateException("audit down");
            }
            batches.add(events);
        }

        List<String> ids() {
            return batches.stream().flatMap(List::stream).map(CentralAuditEvent::eventId).toList();
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RecordingSink sink = new RecordingSink();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private NamedParameterJdbcTemplate jdbc;
    private CentralAuditOutbox outbox;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource("jdbc:h2:mem:audit_outbox_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__central_audit_outbox.sql")).execute(ds);
        jdbc = new NamedParameterJdbcTemplate(ds);
        outbox = new JdbcCentralAuditOutbox(jdbc);
    }

    private static CentralAuditProperties props(boolean enabled, String token, int maxRows, int batch) {
        return new CentralAuditProperties(enabled, "http://audit", token, maxRows, 24, batch, 2000, 3000, 2, 5, 60000, 2000, 60000);
    }

    private CentralAuditPublisher publisher(CentralAuditProperties p) {
        return new CentralAuditPublisher(p, outbox, sink, clock, metrics);
    }

    private static CentralAuditEvent event(String id, String tenant) {
        return new CentralAuditEvent(id, "DOMAIN", Instant.parse("2026-11-02T09:59:00Z"), "rider-uam", null, "uam", "IDENTITY",
                "SUCCESS", null, null, null, null, tenant, null, null, "LOGIN", null);
    }

    @Test
    void publishStoresTheEventWithItsTenantAndNothingIsSentUntilTheDrain() {
        CentralAuditPublisher publisher = publisher(props(true, "tok", 100, 10));

        publisher.publish(event("event-01", "org-1"));

        assertThat(publisher.pending()).isEqualTo(1);
        assertThat(sink.batches).isEmpty();
        assertThat(jdbc.getJdbcTemplate().queryForObject("select tenant_id from central_audit_outbox", String.class)).isEqualTo("org-1");
    }

    @Test
    void drainSendsInOrderInBatchesKeepsTheTenantAndDeletesAfterSuccess() {
        CentralAuditPublisher publisher = publisher(props(true, "tok", 100, 2));
        for (int i = 1; i <= 5; i++) {
            publisher.publish(event("event-0" + i, "org-" + i));
            clock.advance(Duration.ofMillis(10));
        }

        publisher.flush();

        assertThat(sink.batches).extracting(List::size).containsExactly(2, 2, 1);
        assertThat(sink.ids()).containsExactly("event-01", "event-02", "event-03", "event-04", "event-05");
        assertThat(sink.batches.get(0).get(1).tenantId()).isEqualTo("org-2");
        assertThat(sink.batches.get(0).get(0).occurredAt()).isEqualTo(Instant.parse("2026-11-02T09:59:00Z"));
        assertThat(publisher.pending()).isZero();
    }

    @Test
    void aFailedPushKeepsTheRowsAndBacksOffWithGrowingDelay() {
        CentralAuditPublisher publisher = publisher(props(true, "tok", 100, 10));
        publisher.publish(event("event-01", "org-1"));
        sink.failing = true;

        publisher.flush();
        assertThat(publisher.pending()).isEqualTo(1);
        assertThat(metrics.counter(CentralAuditConstants.METRIC_SEND_FAILED).count()).isEqualTo(1);

        sink.failing = false;
        publisher.flush(); // still inside the 2s backoff: not retried
        assertThat(sink.batches).isEmpty();

        clock.advance(Duration.ofMillis(2001));
        sink.failing = true;
        publisher.flush();
        assertThat(jdbc.getJdbcTemplate().queryForObject("select attempts from central_audit_outbox", Integer.class)).isEqualTo(2);

        sink.failing = false;
        clock.advance(Duration.ofMillis(2001)); // second delay is 4s: still waiting
        publisher.flush();
        assertThat(sink.batches).isEmpty();

        clock.advance(Duration.ofMillis(2000));
        publisher.flush();
        assertThat(sink.ids()).containsExactly("event-01");
        assertThat(publisher.pending()).isZero();
    }

    @Test
    void twoInstancesDrainingTheSameOutboxPublishEachEventOnce() {
        CentralAuditPublisher a = publisher(props(true, "tok", 100, 10));
        CentralAuditPublisher b = new CentralAuditPublisher(props(true, "tok", 100, 10),
                new JdbcCentralAuditOutbox(jdbc), sink, clock, metrics);
        a.publish(event("event-01", "org-1"));
        a.publish(event("event-02", "org-1"));
        // while instance A is in the middle of its push, instance B runs its drain
        sink.duringSend = () -> {
            sink.duringSend = () -> {
            };
            b.flush();
        };

        a.flush();

        assertThat(sink.ids()).containsExactly("event-01", "event-02");
        assertThat(a.pending()).isZero();
    }

    @Test
    void aLeaseThatExpiredLetsAnotherInstanceTakeTheRowsOver() {
        outbox.add("event-01", "org-1", CentralAuditPayloadCodec.toJson(event("event-01", "org-1")), clock.instant());
        assertThat(outbox.claim(10, clock.instant(), Duration.ofSeconds(60))).hasSize(1);
        assertThat(outbox.claim(10, clock.instant(), Duration.ofSeconds(60))).isEmpty();

        clock.advance(Duration.ofSeconds(61));

        assertThat(outbox.claim(10, clock.instant(), Duration.ofSeconds(60))).extracting(CentralAuditOutbox.Entry::eventId)
                .containsExactly("event-01");
    }

    @Test
    void aRepeatedEventIdIsStoredOnce() {
        CentralAuditPublisher publisher = publisher(props(true, "tok", 100, 10));
        publisher.publish(event("event-01", "org-1"));
        publisher.publish(event("event-01", "org-1"));

        assertThat(publisher.pending()).isEqualTo(1);
    }

    @Test
    void overTheLimitOnlyRowsPastTheRetentionAreDroppedAndFreshOnesAreKept() {
        CentralAuditPublisher publisher = publisher(props(true, "", 3, 10)); // blank token: bound is still enforced, nothing is sent
        publisher.publish(event("old-1", "org-1"));
        publisher.publish(event("old-2", "org-1"));
        clock.advance(Duration.ofHours(25));
        publisher.publish(event("new-1", "org-1"));
        publisher.publish(event("new-2", "org-1"));

        publisher.flush();

        assertThat(jdbc.getJdbcTemplate().queryForList("select event_id from central_audit_outbox order by event_id", String.class))
                .containsExactly("new-1", "new-2", "old-2");
        assertThat(metrics.counter(CentralAuditConstants.METRIC_DROPPED, "reason", "retention").count()).isEqualTo(1);
        assertThat(metrics.counter(CentralAuditConstants.METRIC_OVERFLOW).count()).isEqualTo(1);
    }

    @Test
    void freshRowsAreNeverDroppedEvenWhenOverTheLimit() {
        CentralAuditPublisher publisher = publisher(props(true, "", 1, 10));
        publisher.publish(event("event-01", "org-1"));
        publisher.publish(event("event-02", "org-1"));
        publisher.publish(event("event-03", "org-1"));

        publisher.flush();

        assertThat(publisher.pending()).isEqualTo(3);
        assertThat(metrics.counter(CentralAuditConstants.METRIC_OVERFLOW).count()).isEqualTo(1);
    }

    @Test
    void anOutboxInsertFailureIsCountedAndNeverThrown() {
        CentralAuditOutbox broken = new JdbcCentralAuditOutbox(jdbc) {
            @Override
            public void add(String eventId, String tenantId, String payload, Instant now) {
                throw new IllegalStateException("db down");
            }
        };
        CentralAuditPublisher publisher = new CentralAuditPublisher(props(true, "tok", 100, 10), broken, sink, clock, metrics);

        publisher.publish(event("event-01", "org-1"));

        assertThat(metrics.counter(CentralAuditConstants.METRIC_INSERT_FAILED).count()).isEqualTo(1);
    }

    @Test
    void anUnreadablePayloadIsRemovedAndDoesNotBlockTheRest() {
        outbox.add("bad-1", "org-1", "{not json", clock.instant());
        CentralAuditPublisher publisher = publisher(props(true, "tok", 100, 10));
        publisher.publish(event("event-01", "org-1"));

        publisher.flush();

        assertThat(sink.ids()).containsExactly("event-01");
        assertThat(publisher.pending()).isZero();
        assertThat(metrics.counter(CentralAuditConstants.METRIC_DROPPED, "reason", "unreadable").count()).isEqualTo(1);
    }

    @Test
    void nothingIsStoredOrSentWhenDisabledAndNothingIsSentWithoutAToken() {
        CentralAuditPublisher disabled = publisher(props(false, "tok", 100, 10));
        disabled.publish(event("event-01", "org-1"));
        disabled.flush();
        assertThat(disabled.pending()).isZero();

        CentralAuditPublisher noToken = publisher(props(true, "", 100, 10));
        noToken.publish(event("event-02", "org-1"));
        noToken.publish(null);
        noToken.flush();
        assertThat(noToken.pending()).isEqualTo(1);
        assertThat(sink.batches).isEmpty();
    }
}
