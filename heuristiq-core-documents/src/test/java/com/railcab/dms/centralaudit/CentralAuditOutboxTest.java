package com.railcab.dms.centralaudit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Outbox enqueue, drain, retry and idempotency, and the shape of the event that is sent. */
class CentralAuditOutboxTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MutableClock clock = new MutableClock(START);
    private final InMemoryAuditOutboxStore store = new InMemoryAuditOutboxStore();
    private final List<List<String>> sent = new ArrayList<>();
    private boolean sinkDown;
    private final AuditEventSink sink = batch -> {
        if (sinkDown) {
            throw new AuditEventSink.AuditSinkException("audit service unavailable", null);
        }
        sent.add(batch);
    };

    private static AuditPublishProperties props(boolean enabled, String token, int batch, int maxAttempts) {
        return new AuditPublishProperties(enabled, "", "http://RealBoom-AuditService", token, batch, 5000, 60, maxAttempts, 15,
                900, 7, 3000, 3);
    }

    private CentralAuditPublisher publisher(AuditPublishProperties p) {
        return new CentralAuditPublisher(store, MAPPER, p, clock);
    }

    private AuditOutboxDrainer drainer(AuditPublishProperties p) {
        return new AuditOutboxDrainer(store, sink, p, clock);
    }

    private static DmsAuditEvent event(String eventId, String tenant) {
        return new DmsAuditEvent(eventId, CentralAuditConstants.EVENT_TYPE, START.toString(), CentralAuditConstants.SOURCE_SERVICE,
                "user-1", "CUSTOMER", CentralAuditConstants.FEATURE, CentralAuditConstants.MODULE, "FAILURE", 12L, "corr-1",
                CentralAuditConstants.APP_CHANNEL, tenant, "DOCUMENT", "doc-1", "FILE_TOO_LARGE",
                Map.of("action", "UPLOAD_REJECTED", "documentType", "PHOTO_ID", "sizeBytes", 99L));
    }

    @BeforeEach
    void ready() {
        sinkDown = false;
    }

    @Test
    void queuedPayloadCarriesTheAgreedContractFields() throws Exception {
        publisher(props(true, "t", 100, 5)).publish(event("dms-abc12345", "org-a"));

        JsonNode json = MAPPER.readTree(store.all().get(0).payload());
        assertEquals("DOMAIN", json.get("eventType").asText());
        assertEquals("rider-dms", json.get("sourceService").asText());
        assertEquals("dms", json.get("feature").asText());
        assertEquals("DOCUMENTS", json.get("module").asText());
        assertEquals("SERVICE", json.get("appChannel").asText());
        assertEquals("FAILURE", json.get("outcome").asText());
        assertEquals("org-a", json.get("tenantId").asText());
        assertEquals("corr-1", json.get("correlationId").asText());
        assertEquals("user-1", json.get("userId").asText());
        assertEquals(12, json.get("durationMs").asInt());
        assertEquals("DOCUMENT", json.get("subjectType").asText());
        assertEquals("doc-1", json.get("subjectId").asText());
        assertEquals("FILE_TOO_LARGE", json.get("reasonCode").asText());
        assertEquals("PHOTO_ID", json.get("details").get("documentType").asText());
        assertEquals(99, json.get("details").get("sizeBytes").asInt());
    }

    @Test
    void queueingTheSameEventIdTwiceStoresItOnce() {
        CentralAuditPublisher publisher = publisher(props(true, "t", 100, 5));

        publisher.publish(event("dms-abc12345", "org-a"));
        publisher.publish(event("dms-abc12345", "org-a"));

        assertEquals(1, store.all().size());
    }

    @Test
    void eventsWithoutAnOrganizationOrWhenDisabledAreNotQueued() {
        publisher(props(true, "t", 100, 5)).publish(event("dms-abc12345", null));
        publisher(props(true, "t", 100, 5)).publish(event("dms-abc12346", " "));
        publisher(props(false, "t", 100, 5)).publish(event("dms-abc12347", "org-a"));

        assertTrue(store.all().isEmpty());
    }

    @Test
    void aBrokenOutboxNeverFailsTheCaller() {
        store.failInserts = true;

        assertDoesNotThrow(() -> publisher(props(true, "t", 100, 5)).publish(event("dms-abc12345", "org-a")));
    }

    @Test
    void drainSendsDueEventsInBatchesAndMarksThemSent() {
        AuditPublishProperties p = props(true, "t", 2, 5);
        for (int i = 0; i < 5; i++) {
            publisher(p).publish(event("dms-abc1234" + i, "org-a"));
            clock.advance(Duration.ofSeconds(1));
        }

        assertEquals(2, drainer(p).drain());
        assertEquals(2, drainer(p).drain());
        assertEquals(1, drainer(p).drain());
        assertEquals(0, drainer(p).drain());

        assertEquals(3, sent.size());
        assertEquals(5, store.countByStatus(OutboxStatus.SENT));
        assertEquals(0, store.countByStatus(OutboxStatus.PENDING));
    }

    @Test
    void anotherDrainerCannotTakeAnEntryWhileItIsLeased() {
        AuditPublishProperties p = props(true, "t", 100, 5);
        publisher(p).publish(event("dms-abc12345", "org-a"));
        OutboxEntry due = store.findDue(clock.instant(), 10).get(0);

        assertTrue(store.tryLease(due.id(), "instance-1", clock.instant(), clock.instant().plusSeconds(60)));
        assertEquals(false, store.tryLease(due.id(), "instance-2", clock.instant(), clock.instant().plusSeconds(60)));
        assertEquals(0, drainer(p).drain(), "a leased entry is not sent by a second instance");

        clock.advance(Duration.ofSeconds(61));
        assertEquals(1, drainer(p).drain(), "an expired lease can be taken again");
    }

    @Test
    void failedPushKeepsTheEventAndRetriesWithExponentialBackoff() {
        AuditPublishProperties p = props(true, "t", 100, 5);
        publisher(p).publish(event("dms-abc12345", "org-a"));
        sinkDown = true;

        assertEquals(0, drainer(p).drain());
        OutboxEntry first = store.all().get(0);
        assertEquals(1, first.attempts());
        assertEquals(OutboxStatus.PENDING, first.status());
        assertEquals(START.plusSeconds(15), first.nextAttemptAt());
        assertNull(first.lockedUntil());

        assertEquals(0, drainer(p).drain(), "not due yet");
        assertEquals(1, store.all().get(0).attempts());

        clock.advance(Duration.ofSeconds(15));
        drainer(p).drain();
        assertEquals(START.plusSeconds(15).plusSeconds(30), store.all().get(0).nextAttemptAt());
        assertEquals(2, store.all().get(0).attempts());

        sinkDown = false;
        clock.advance(Duration.ofSeconds(30));
        assertEquals(1, drainer(p).drain());
        assertEquals(OutboxStatus.SENT, store.all().get(0).status());
    }

    @Test
    void backoffIsCappedAtTheConfiguredMaximum() {
        AuditOutboxDrainer d = drainer(props(true, "t", 100, 50));

        assertEquals(15, d.backoffSeconds(1));
        assertEquals(30, d.backoffSeconds(2));
        assertEquals(900, d.backoffSeconds(20));
    }

    @Test
    void anEventIsMarkedDeadAfterTheMaximumAttempts() {
        AuditPublishProperties p = props(true, "t", 100, 2);
        publisher(p).publish(event("dms-abc12345", "org-a"));
        sinkDown = true;

        drainer(p).drain();
        clock.advance(Duration.ofSeconds(15));
        drainer(p).drain();

        assertEquals(OutboxStatus.DEAD, store.all().get(0).status());
        clock.advance(Duration.ofDays(1));
        assertEquals(0, drainer(p).drain(), "a dead entry is never sent again");
    }

    @Test
    void nothingIsSentWhileTheServiceTokenIsBlankOrPublishingIsOff() {
        publisher(props(true, "t", 100, 5)).publish(event("dms-abc12345", "org-a"));

        assertEquals(0, drainer(props(true, "", 100, 5)).drain());
        assertEquals(0, drainer(props(false, "t", 100, 5)).drain());

        assertTrue(sent.isEmpty());
        assertEquals(1, store.countByStatus(OutboxStatus.PENDING));
    }

    @Test
    void expiredEntriesArePurgedAndRetentionIsApplied() {
        AuditPublishProperties p = props(true, "t", 100, 5);
        publisher(p).publish(event("dms-abc12345", "org-a"));
        assertEquals(START.plus(Duration.ofDays(7)), store.all().get(0).expireAt());

        clock.advance(Duration.ofDays(8));
        sinkDown = true;
        drainer(p).drain();

        assertTrue(store.all().isEmpty());
        assertTrue(store.purgeCalls > 0);
    }

    @Test
    void aSinkThatThrowsAnythingNeverEscapesTheDrain() {
        AuditPublishProperties p = props(true, "t", 100, 5);
        publisher(p).publish(event("dms-abc12345", "org-a"));
        AuditEventSink exploding = batch -> {
            throw new IllegalStateException("boom");
        };

        assertDoesNotThrow(() -> new AuditOutboxDrainer(store, exploding, p, clock).drain());
        assertEquals(1, store.all().get(0).attempts());
    }
}
