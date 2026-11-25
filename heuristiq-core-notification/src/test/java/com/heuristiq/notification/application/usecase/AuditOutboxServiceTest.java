package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.port.out.AuditEventSink;
import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

class AuditOutboxServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

    private InMemoryOutboxRepository outbox;
    private NmsProperties props;
    private final List<List<String>> sent = new ArrayList<>();
    private RuntimeException sinkFailure;
    private AuditOutboxService service;

    @BeforeEach
    void setUp() {
        outbox = new InMemoryOutboxRepository();
        props = new NmsProperties();
        props.getAudit().getPublish().setServiceToken("token");
        AuditEventSink sink = events -> {
            if (sinkFailure != null) {
                throw sinkFailure;
            }
            sent.add(events);
        };
        service = new AuditOutboxService(outbox, sink, props, CLOCK);
    }

    private void queue(String eventId) {
        outbox.insertIfAbsent(AuditOutboxEntry.builder().eventId(eventId).payload("{\"eventId\":\"" + eventId + "\"}")
                .status(AuditOutboxStatus.PENDING).nextAttemptAt(NOW).createdAt(NOW).expireAt(NOW.plusDays(7)).build());
    }

    @Test
    void dueEventsAreSentInOneBatchAndMarkedSent() {
        queue("evt-00000001");
        queue("evt-00000002");

        int accepted = service.drain();

        assertThat(accepted).isEqualTo(2);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).hasSize(2);
        assertThat(outbox.countByStatus(AuditOutboxStatus.SENT)).isEqualTo(2);
        assertThat(outbox.countByStatus(AuditOutboxStatus.PENDING)).isZero();
    }

    @Test
    void anAuditServiceOutageKeepsTheEventsAndSchedulesARetryWithBackoff() {
        queue("evt-00000001");
        sinkFailure = new AuditEventSink.AuditSinkException("audit down", new RuntimeException());

        assertThat(service.drain()).isZero();

        AuditOutboxEntry entry = outbox.all().get(0);
        assertThat(entry.getStatus()).isEqualTo(AuditOutboxStatus.PENDING);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(entry.getLastError()).contains("audit down");
        assertThat(entry.getLockedUntil()).isNull();
        // not due again until the backoff passes
        sinkFailure = null;
        assertThat(service.drain()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void backoffDoublesAndIsCapped() {
        NmsProperties.Audit.Publish cfg = props.getAudit().getPublish();
        assertThat(service.backoffSeconds(cfg, 1)).isEqualTo(15);
        assertThat(service.backoffSeconds(cfg, 2)).isEqualTo(30);
        assertThat(service.backoffSeconds(cfg, 3)).isEqualTo(60);
        assertThat(service.backoffSeconds(cfg, 30)).isEqualTo(cfg.getRetryMaxBackoffSeconds());
    }

    @Test
    void anEventThatKeepsFailingIsEventuallyMarkedDead() {
        props.getAudit().getPublish().setMaxAttempts(1);
        queue("evt-00000001");
        sinkFailure = new AuditEventSink.AuditSinkException("audit down", new RuntimeException());

        service.drain();

        assertThat(outbox.countByStatus(AuditOutboxStatus.DEAD)).isEqualTo(1);
    }

    @Test
    void anEntryLeasedByAnotherInstanceIsNotSentTwice() {
        queue("evt-00000001");
        String id = outbox.all().get(0).getId();
        assertThat(outbox.tryLease(id, "other-instance", NOW, NOW.plusSeconds(60))).isTrue();

        assertThat(service.drain()).isZero();

        assertThat(sent).isEmpty();
    }

    @Test
    void anExpiredLeaseIsPickedUpAgain() {
        queue("evt-00000001");
        String id = outbox.all().get(0).getId();
        outbox.tryLease(id, "crashed-instance", NOW.minusSeconds(120), NOW.minusSeconds(60));

        assertThat(service.drain()).isEqualTo(1);
    }

    @Test
    void nothingHappensWithoutAServiceToken() {
        props.getAudit().getPublish().setServiceToken("");
        queue("evt-00000001");

        assertThat(service.drain()).isZero();

        assertThat(sent).isEmpty();
        assertThat(outbox.countByStatus(AuditOutboxStatus.PENDING)).isEqualTo(1);
    }

    @Test
    void nothingHappensWhenDisabled() {
        props.getAudit().getPublish().setEnabled(false);
        queue("evt-00000001");

        assertThat(service.drain()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void anUnexpectedErrorInTheStoreNeverEscapesTheScheduler() {
        InMemoryOutboxRepository broken = new InMemoryOutboxRepository() {
            @Override
            public synchronized List<AuditOutboxEntry> findDue(LocalDateTime now, int limit) {
                throw new IllegalStateException("db down");
            }
        };
        AuditOutboxService svc = new AuditOutboxService(broken, events -> { }, props, CLOCK);

        assertThatCode(svc::drain).doesNotThrowAnyException();
    }
}
