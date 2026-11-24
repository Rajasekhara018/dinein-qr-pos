package com.heuristiq.paymentservice.gatewayaudit.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.paymentservice.gatewayaudit.AuditOutboxWriter;
import com.heuristiq.paymentservice.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.paymentservice.gatewayaudit.GatewayAuditEvent;
import com.heuristiq.paymentservice.gatewayaudit.model.PaymentAuditOutboxEntry;
import com.heuristiq.paymentservice.gatewayaudit.repository.PaymentAuditOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Drain of the audit outbox: batching, idempotent event ids, backoff when the audit service is down. */
class AuditOutboxPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    private PaymentAuditOutboxRepository outbox;
    private AuditEventSink sink;
    private AuditPublishProperties props;
    private AuditOutboxPublisher publisher;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        outbox = mock(PaymentAuditOutboxRepository.class);
        sink = mock(AuditEventSink.class);
        props = new AuditPublishProperties();
        props.setBatchSize(3);
        props.setMaxBatchesPerRun(5);
        meters = new SimpleMeterRegistry();
        publisher = new AuditOutboxPublisher(outbox, sink, props, CLOCK, meters);
    }

    private static List<PaymentAuditOutboxEntry> entries(int from, int count) {
        return IntStream.range(from, from + count).mapToObj(i -> PaymentAuditOutboxEntry.builder()
                .id((long) i).eventId("evt-" + i).payload("{\"eventId\":\"evt-" + i + "\"}")
                .attempts(0).createdAt(NOW).nextAttemptAt(NOW).build()).toList();
    }

    @Test
    void sendsDueEntriesInBatchesOfTheConfiguredSizeAndMarksThemPublished() {
        when(outbox.findDue(eq(NOW), any(Pageable.class)))
                .thenReturn(entries(1, 3)).thenReturn(entries(4, 3)).thenReturn(entries(7, 1));
        when(sink.send(any())).thenReturn(new AuditEventSink.IngestResult(3, 3, 0, 0));

        int sent = publisher.drain();

        assertThat(sent).isEqualTo(7);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(outbox, org.mockito.Mockito.times(3)).findDue(eq(NOW), page.capture());
        assertThat(page.getAllValues()).allSatisfy(p -> assertThat(p.getPageSize()).isEqualTo(3));
        ArgumentCaptor<List<String>> payloads = ArgumentCaptor.forClass(List.class);
        verify(sink, org.mockito.Mockito.times(3)).send(payloads.capture());
        assertThat(payloads.getAllValues().get(0)).hasSize(3);
        assertThat(payloads.getAllValues().get(2)).hasSize(1);
        verify(outbox).markPublished(List.of(1L, 2L, 3L), NOW);
        verify(outbox).markPublished(List.of(7L), NOW);
        assertThat(meters.counter(GatewayAuditConstants.METRIC_PUBLISHED).count()).isEqualTo(7.0);
    }

    @Test
    void nothingDue_sendsNothing() {
        when(outbox.findDue(any(), any(Pageable.class))).thenReturn(List.of());

        assertThat(publisher.drain()).isZero();

        verify(sink, never()).send(any());
    }

    @Test
    void auditServiceDown_keepsEntriesWithBackoffAndNeverThrows() {
        List<PaymentAuditOutboxEntry> due = entries(1, 2);
        due.get(1).setAttempts(2);
        when(outbox.findDue(any(), any(Pageable.class))).thenReturn(due);
        when(sink.send(any())).thenThrow(new IllegalStateException("audit down"));

        int sent = publisher.drain();

        assertThat(sent).isZero();
        verify(outbox, never()).markPublished(anyCollection(), any());
        // attempts = max(0, 2) + 1 = 3 -> 2^3 = 8 seconds
        verify(outbox).markFailed(List.of(1L, 2L), NOW.plusSeconds(8));
        assertThat(meters.counter(GatewayAuditConstants.METRIC_PUBLISH_FAILURES).count()).isEqualTo(1.0);
        // a failure ends the run: no hammering of an unavailable service
        verify(sink, org.mockito.Mockito.times(1)).send(any());
    }

    @Test
    void backoffIsCapped() {
        props.setMaxBackoffSeconds(60);
        List<PaymentAuditOutboxEntry> due = entries(1, 1);
        due.get(0).setAttempts(30);
        when(outbox.findDue(any(), any(Pageable.class))).thenReturn(due);
        when(sink.send(any())).thenThrow(new IllegalStateException("audit down"));

        publisher.drain();

        verify(outbox).markFailed(List.of(1L), NOW.plusSeconds(60));
    }

    @Test
    void databaseFailureWhileReading_isSwallowed() {
        when(outbox.findDue(any(), any(Pageable.class))).thenThrow(new IllegalStateException("db down"));

        assertThat(publisher.drain()).isZero();
        assertThat(meters.counter(GatewayAuditConstants.METRIC_PUBLISH_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void disabled_doesNotTouchTheOutboxOrTheNetwork() {
        props.setEnabled(false);

        assertThat(publisher.drain()).isZero();

        verify(outbox, never()).findDue(any(), any());
        verify(sink, never()).send(any());
    }

    @Test
    void purgeDeletesPublishedRowsOlderThanTheRetentionWindow() {
        props.setPublishedRetentionDays(7);
        when(outbox.deletePublishedBefore(NOW.minusDays(7))).thenReturn(12);

        assertThat(publisher.purge()).isEqualTo(12);
    }

    @Test
    void eventIdsAreDeterministicSoRedeliveryIsIdempotentAtTheAuditService() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuditOutboxWriter writer = new AuditOutboxWriter(outbox, mapper, CLOCK);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("gateway", "RAZORPAY");
        GatewayAuditEvent event = new GatewayAuditEvent("call-123", CLOCK.instant(), "SUCCESS", 200, 42L, "corr-1", details);

        String first = writer.toJson(event);
        String second = writer.toJson(event);

        assertThat(second).isEqualTo(first);
        JsonNode json = mapper.readTree(first);
        assertThat(json.get("eventId").asText()).isEqualTo("call-123");
        assertThat(json.get("eventType").asText()).isEqualTo("PAYMENT_GATEWAY");
        assertThat(json.get("sourceService").asText()).isEqualTo("payment-service");
        assertThat(json.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("statusCode").asInt()).isEqualTo(200);
        assertThat(json.get("durationMs").asLong()).isEqualTo(42L);
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-06T10:00:00Z");
        assertThat(json.get("details").get("gateway").asText()).isEqualTo("RAZORPAY");
    }
}
