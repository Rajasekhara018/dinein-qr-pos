package com.railcab.nms.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.railcab.nms.domain.model.AuditOutboxEntry;
import com.railcab.nms.domain.model.AuditOutboxStatus;
import com.railcab.nms.domain.model.DeliveryStatus;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.testsupport.InMemoryOutboxRepository;

class CommunicationAuditPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private InMemoryOutboxRepository outbox;
    private NmsProperties props;
    private CommunicationAuditPublisher publisher;

    @BeforeEach
    void setUp() {
        outbox = new InMemoryOutboxRepository();
        props = new NmsProperties();
        publisher = new CommunicationAuditPublisher(outbox, mapper, props, CLOCK);
    }

    private NotificationAttempt failedSmsAttempt() {
        return NotificationAttempt.builder()
                .id("6f1c2b0e-8a54-4d0e-9d1a-3c1f6a2b7e10")
                .logId("log-1")
                .messageId("msg-1")
                .correlationId("corr-1")
                .event("PAYMENT_CAPTURED")
                .sourceService("rider-payment")
                .eventCode("PAYMENT_CAPTURED")
                .triggeringUserId("u-42")
                .entityType("BOOKING")
                .entityId("b-9")
                .channel(NotificationChannel.SMS)
                .providerName("digimile")
                .attemptNumber(2)
                .status(DeliveryStatus.FAILED)
                .errorCode("DIGIMILE_1707")
                .errorMessage("Digimile response code 1707 password=hunter2")
                .recipient("9876543210")
                .latencyMs(120L)
                .startedAt(LocalDateTime.of(2026, 10, 6, 9, 59, 58))
                .completedAt(LocalDateTime.of(2026, 10, 6, 9, 59, 59))
                .build();
    }

    @Test
    void anAttemptOutcomeIsQueuedAsACommunicationEventKeyedByTheAttemptId() throws Exception {
        publisher.publishAttemptOutcome(failedSmsAttempt());

        assertThat(outbox.all()).hasSize(1);
        AuditOutboxEntry entry = outbox.all().get(0);
        assertThat(entry.getEventId()).isEqualTo("6f1c2b0e-8a54-4d0e-9d1a-3c1f6a2b7e10");
        assertThat(entry.getStatus()).isEqualTo(AuditOutboxStatus.PENDING);
        assertThat(entry.getExpireAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 0).plusDays(7));

        JsonNode json = mapper.readTree(entry.getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo("COMMUNICATION");
        assertThat(json.path("sourceService").asText()).isEqualTo("rider-nms");
        assertThat(json.path("outcome").asText()).isEqualTo("FAILURE");
        assertThat(json.path("userId").asText()).isEqualTo("u-42");
        assertThat(json.path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(json.path("durationMs").asLong()).isEqualTo(120L);
        assertThat(json.path("occurredAt").asText()).startsWith("2026-10-06T09:59:59");
        JsonNode d = json.path("details");
        assertThat(d.path("channel").asText()).isEqualTo("SMS");
        assertThat(d.path("templateCode").asText()).isEqualTo("PAYMENT_CAPTURED");
        assertThat(d.path("originService").asText()).isEqualTo("rider-payment");
        assertThat(d.path("provider").asText()).isEqualTo("digimile");
        assertThat(d.path("attempt").asText()).isEqualTo("2");
        assertThat(d.path("entityType").asText()).isEqualTo("BOOKING");
        assertThat(d.path("failureReason").asText()).contains("1707").doesNotContain("hunter2");
        assertThat(d.size()).isLessThanOrEqualTo(20);
    }

    @Test
    void theEventCarriesTheOrganizationOfTheNotification() {
        NotificationAttempt attempt = failedSmsAttempt();
        attempt.setTenantId("org-7");

        assertThat(publisher.toEvent(attempt, "evt-1").tenantId()).isEqualTo("org-7");
        assertThat(publisher.toEvent(failedSmsAttempt(), "evt-2").tenantId()).isNull();
    }

    @Test
    void theEventNeverCarriesAFullRecipientOrABody() {
        NotificationAttempt attempt = failedSmsAttempt();
        attempt.setRecipient("jane.doe@example.com");

        publisher.publishAttemptOutcome(attempt);

        String payload = outbox.all().get(0).getPayload();
        assertThat(payload).doesNotContain("jane.doe@example.com").contains("j***@example.com");
        assertThat(payload).doesNotContain("9876543210").doesNotContainIgnoringCase("renderedBody");
    }

    @Test
    void aStatusUpdateGetsADeterministicIdWithTheStatusSuffix() {
        NotificationAttempt attempt = failedSmsAttempt();
        attempt.setStatus(DeliveryStatus.DELIVERED);
        attempt.setStatusUpdatedAt(LocalDateTime.of(2026, 10, 6, 10, 0, 5));

        publisher.publishStatusUpdate(attempt);
        publisher.publishStatusUpdate(attempt);

        assertThat(outbox.all()).hasSize(1);
        assertThat(outbox.all().get(0).getEventId()).isEqualTo("6f1c2b0e-8a54-4d0e-9d1a-3c1f6a2b7e10_DELIVERED");
        assertThat(outbox.all().get(0).getPayload()).contains("\"outcome\":\"SUCCESS\"");
    }

    @Test
    void theEventIdFitsTheAuditServiceRules() {
        NotificationAttempt attempt = failedSmsAttempt();
        attempt.setId("a".repeat(80));

        publisher.publishAttemptOutcome(attempt);

        assertThat(outbox.all().get(0).getEventId()).matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void nothingIsQueuedWhenPublishingIsDisabled() {
        props.getAudit().getPublish().setEnabled(false);

        publisher.publishAttemptOutcome(failedSmsAttempt());

        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void aBrokenOutboxNeverFailsTheCaller() {
        outbox.failInserts = new IllegalStateException("database down");

        assertThatCode(() -> publisher.publishAttemptOutcome(failedSmsAttempt())).doesNotThrowAnyException();
    }
}
