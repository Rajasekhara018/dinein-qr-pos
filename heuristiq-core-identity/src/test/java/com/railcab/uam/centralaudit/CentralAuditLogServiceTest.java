package com.heuristiq.uam.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.enums.UamEnums.AuditAction;

/** UAM hands domain audit entries to the central audit service and never fails the caller. */
class CentralAuditLogServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final List<CentralAuditEvent> sent = new ArrayList<>();
    private final List<CentralAuditOutbox.Entry> stored = new ArrayList<>();
    private final CentralAuditOutbox outbox = new CentralAuditOutbox() {
        @Override
        public void add(String eventId, String tenantId, String payload, Instant now) {
            stored.add(new Entry(eventId, tenantId, payload, 0));
        }

        @Override
        public List<Entry> claim(int max, Instant now, java.time.Duration lease) {
            List<Entry> out = new ArrayList<>(stored);
            stored.clear();
            return out;
        }

        @Override
        public void delete(List<String> eventIds) {
        }

        @Override
        public void reschedule(String eventId, int attempts, Instant nextAttemptAt) {
        }

        @Override
        public long count() {
            return stored.size();
        }

        @Override
        public int deleteOldest(Instant olderThan, int max) {
            return 0;
        }
    };
    private final CentralAuditPublisher publisher = new CentralAuditPublisher(
            new CentralAuditProperties(true, "http://audit", "tok", 100, 24, 10, 2000, 3000, 2, 5, 60000, 2000, 60000), outbox,
            events -> sent.addAll(events), Clock.fixed(NOW, ZoneOffset.UTC), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    private final CentralAuditLogService service = new CentralAuditLogService(publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anEntryBecomesAQueuedEventStampedWithTheClockWhenItHasNoTime() {
        AuditLog entry = AuditLog.builder().tenantId("org-1").action(AuditAction.ASSIGN_ROLE).build();

        AuditLogServiceUseCase.RecordOutput out = service.record(new AuditLogServiceUseCase.RecordInput(entry));
        publisher.flush();

        assertThat(out.entry()).isSameAs(entry);
        assertThat(sent).singleElement().satisfies(e -> {
            assertThat(e.occurredAt()).isEqualTo(NOW);
            assertThat(e.reasonCode()).isEqualTo("ASSIGN_ROLE");
            assertThat(e.tenantId()).isEqualTo("org-1");
        });
    }

    @Test
    void anEntryTimeIsKeptAsUtc() {
        service.record(new AuditLogServiceUseCase.RecordInput(
                AuditLog.builder().createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5)).build()));
        publisher.flush();

        assertThat(sent.get(0).occurredAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
    }

    @Test
    void aBrokenEntryIsSwallowed() {
        assertThatCode(() -> service.record(new AuditLogServiceUseCase.RecordInput(null))).doesNotThrowAnyException();
    }
}
