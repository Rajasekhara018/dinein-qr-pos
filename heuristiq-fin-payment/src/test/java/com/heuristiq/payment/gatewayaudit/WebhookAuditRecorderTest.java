package com.heuristiq.payment.gatewayaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.gatewayaudit.model.PaymentAuditOutboxEntry;
import com.heuristiq.payment.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.payment.gatewayaudit.repository.PaymentAuditOutboxRepository;
import com.heuristiq.payment.payment.model.PaymentWebhookEvent;
import com.heuristiq.payment.payment.repository.PaymentWebhookEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Webhook delivery logging: rows without payload/signature, audit event content, and failure isolation. */
class WebhookAuditRecorderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private PaymentWebhookEventRepository events;
    private PaymentAuditOutboxRepository outboxRepository;
    private SimpleMeterRegistry meters;
    private WebhookAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        events = mock(PaymentWebhookEventRepository.class);
        outboxRepository = mock(PaymentAuditOutboxRepository.class);
        meters = new SimpleMeterRegistry();
        recorder = new WebhookAuditRecorder(events, new AuditOutboxWriter(outboxRepository, new ObjectMapper(), CLOCK),
                mock(PlatformTransactionManager.class), CLOCK, meters);
    }

    @Test
    void rejectedDelivery_storesRowWithoutDedupeKeyAndQueuesFailureEvent() {
        recorder.recordUndelivered(new WebhookObservation("RAZORPAY", null, "evt-1", false, WebhookOutcome.REJECTED, null));

        ArgumentCaptor<PaymentWebhookEvent> row = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(events).save(row.capture());
        assertThat(row.getValue().getProviderEventId()).isNull();
        assertThat(row.getValue().getClaimedEventId()).isEqualTo("evt-1");
        assertThat(row.getValue().getSignatureValid()).isFalse();
        assertThat(row.getValue().getOutcome()).isEqualTo("REJECTED");

        ArgumentCaptor<PaymentAuditOutboxEntry> queued = ArgumentCaptor.forClass(PaymentAuditOutboxEntry.class);
        verify(outboxRepository).save(queued.capture());
        assertThat(queued.getValue().getEventId()).isEqualTo(row.getValue().getId());
        assertThat(queued.getValue().getPayload())
                .contains("\"outcome\":\"FAILURE\"", "\"failureReason\":\"SIGNATURE_INVALID\"",
                        "\"webhookOutcome\":\"REJECTED\"", "\"signatureValid\":false", "\"operation\":\"WEBHOOK\"");
    }

    @Test
    void duplicateDelivery_queuesSuccessEventWithPaymentId() {
        recorder.recordUndelivered(new WebhookObservation("RAZORPAY", "payment.captured", "evt-2", true,
                WebhookOutcome.DUPLICATE, "pay-1"));

        ArgumentCaptor<PaymentAuditOutboxEntry> queued = ArgumentCaptor.forClass(PaymentAuditOutboxEntry.class);
        verify(outboxRepository).save(queued.capture());
        assertThat(queued.getValue().getPayload()).contains("\"outcome\":\"SUCCESS\"", "\"paymentId\":\"pay-1\"",
                "\"gatewayRef\":\"evt-2\"", "\"webhookOutcome\":\"DUPLICATE\"");
    }

    @Test
    void failureToStore_isSwallowedAndCounted() {
        when(events.save(any(PaymentWebhookEvent.class))).thenThrow(new IllegalStateException("db down"));

        recorder.recordUndelivered(new WebhookObservation("RAZORPAY", null, "evt-3", false, WebhookOutcome.REJECTED, null));

        assertThat(meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void failureToQueueAProcessedEvent_isSwallowedAndCounted() {
        when(outboxRepository.save(any(PaymentAuditOutboxEntry.class))).thenThrow(new IllegalStateException("db down"));

        recorder.publishProcessed("row-1", new WebhookObservation("RAZORPAY", "payment.captured", "evt-4", true,
                WebhookOutcome.PROCESSED, "pay-1"));

        assertThat(meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES).count()).isEqualTo(1.0);
    }
}
