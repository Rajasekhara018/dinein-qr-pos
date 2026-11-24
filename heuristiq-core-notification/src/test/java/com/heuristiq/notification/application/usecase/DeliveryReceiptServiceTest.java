package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.DeliveryReceipt;
import com.heuristiq.notification.application.dto.ReceiptOutcome;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;
import com.heuristiq.notification.testsupport.InMemoryLogRepository;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

class DeliveryReceiptServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 9, 59, 0);

    private InMemoryAttemptRepository attempts;
    private InMemoryLogRepository logs;
    private InMemoryOutboxRepository outbox;
    private DeliveryReceiptService service;
    private String attemptId;
    private String logId;

    @BeforeEach
    void setUp() {
        attempts = new InMemoryAttemptRepository();
        logs = new InMemoryLogRepository();
        outbox = new InMemoryOutboxRepository();
        service = new DeliveryReceiptService(attempts, logs,
                new CommunicationAuditPublisher(outbox, new ObjectMapper().registerModule(new JavaTimeModule()),
                        new NmsProperties(), CLOCK), CLOCK);

        logId = logs.save(NotificationLog.builder().messageId("msg-1").channel(NotificationChannel.SMS)
                .status(DeliveryStatus.SENT).success(true).sentAt(T0).build()).getId();
        attemptId = attempts.save(NotificationAttempt.builder().logId(logId).messageId("msg-1").correlationId("c-1")
                .channel(NotificationChannel.SMS).providerName("digimile").providerMessageId("pm-1")
                .attemptNumber(1).status(DeliveryStatus.SENT).recipient("******3210").startedAt(T0).build()).getId();
    }

    private DeliveryReceipt receipt(DeliveryStatus status) {
        return new DeliveryReceipt("digimile", "pm-1", status, status.name().toLowerCase(), null, null, null);
    }

    @Test
    void aDeliveredReceiptUpdatesTheAttemptTheLogAndQueuesAnAuditEvent() {
        ReceiptOutcome outcome = service.apply(receipt(DeliveryStatus.DELIVERED));

        assertThat(outcome).isEqualTo(ReceiptOutcome.APPLIED);
        NotificationAttempt a = attempts.findById(attemptId).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(a.getProviderStatus()).isEqualTo("delivered");
        assertThat(a.getStatusUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 0, 0));
        assertThat(logs.findById(logId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(outbox.all()).hasSize(1);
        assertThat(outbox.all().get(0).getEventId()).isEqualTo(attemptId + "_DELIVERED");
    }

    @Test
    void theSameReceiptTwiceChangesAndPublishesNothingTheSecondTime() {
        service.apply(receipt(DeliveryStatus.DELIVERED));
        NotificationAttempt afterFirst = attempts.findById(attemptId).orElseThrow();

        ReceiptOutcome second = service.apply(receipt(DeliveryStatus.DELIVERED));

        assertThat(second).isEqualTo(ReceiptOutcome.DUPLICATE);
        assertThat(attempts.findById(attemptId).orElseThrow().getVersion()).isEqualTo(afterFirst.getVersion());
        assertThat(outbox.all()).hasSize(1);
    }

    @Test
    void aLateSentReceiptAfterDeliveredIsIgnored() {
        service.apply(receipt(DeliveryStatus.DELIVERED));

        assertThat(service.apply(receipt(DeliveryStatus.SENT))).isEqualTo(ReceiptOutcome.DUPLICATE);
        assertThat(attempts.findById(attemptId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void aFailureReceiptRecordsTheReasonAndTurnsTheSummaryRowIntoAFailure() {
        DeliveryReceipt failed = new DeliveryReceipt("digimile", "pm-1", DeliveryStatus.FAILED, "UNDELIV", "11",
                "Handset unreachable password=abc", null);

        assertThat(service.apply(failed)).isEqualTo(ReceiptOutcome.APPLIED);

        NotificationAttempt a = attempts.findById(attemptId).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(a.getErrorCode()).isEqualTo("11");
        assertThat(a.getErrorMessage()).contains("Handset unreachable").doesNotContain("abc");
        NotificationLog log = logs.findById(logId).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(log.isSuccess()).isFalse();
        assertThat(outbox.all().get(0).getPayload()).contains("\"outcome\":\"FAILURE\"");
    }

    @Test
    void aReceiptForAnUnknownMessageIsAcknowledgedWithoutSideEffects() {
        DeliveryReceipt unknown = new DeliveryReceipt("digimile", "nope", DeliveryStatus.DELIVERED, "d", null, null, null);

        assertThat(service.apply(unknown)).isEqualTo(ReceiptOutcome.UNKNOWN_MESSAGE);
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void anUnusableReceiptIsInvalid() {
        assertThat(service.apply(null)).isEqualTo(ReceiptOutcome.INVALID);
        assertThat(service.apply(new DeliveryReceipt("p", " ", DeliveryStatus.DELIVERED, null, null, null, null)))
                .isEqualTo(ReceiptOutcome.INVALID);
        assertThat(service.apply(new DeliveryReceipt("p", "pm-1", null, null, null, null, null)))
                .isEqualTo(ReceiptOutcome.INVALID);
    }

    @Test
    void aReceiptThatRacesAnotherWriterIsRetriedAndStillApplied() {
        attempts.optimisticFailuresToInject = 2;

        assertThat(service.apply(receipt(DeliveryStatus.DELIVERED))).isEqualTo(ReceiptOutcome.APPLIED);
        assertThat(attempts.findById(attemptId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void aReceiptThatKeepsLosingTheRaceEndsAsADuplicateInsteadOfFailing() {
        attempts.optimisticFailuresToInject = 10;

        assertThat(service.apply(receipt(DeliveryStatus.DELIVERED))).isEqualTo(ReceiptOutcome.DUPLICATE);
    }

    @Test
    void aBrokenAuditOutboxDoesNotStopTheStatusUpdate() {
        outbox.failInserts = new IllegalStateException("db down");

        assertThat(service.apply(receipt(DeliveryStatus.DELIVERED))).isEqualTo(ReceiptOutcome.APPLIED);
        assertThat(attempts.findById(attemptId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void theProviderTimestampWinsOverTheClock() {
        LocalDateTime providerTime = LocalDateTime.of(2026, 10, 6, 9, 59, 30);

        service.apply(new DeliveryReceipt("digimile", "pm-1", DeliveryStatus.DELIVERED, "d", null, null, providerTime));

        assertThat(attempts.findById(attemptId).orElseThrow().getStatusUpdatedAt()).isEqualTo(providerTime);
    }
}
