package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.ChannelSendOutcome;
import com.heuristiq.notification.application.dto.ChannelSendOutcome.AttemptResult;
import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;
import com.heuristiq.notification.testsupport.InMemoryLogRepository;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

class NotificationAuditRecorderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

    private InMemoryLogRepository logs;
    private InMemoryAttemptRepository attempts;
    private InMemoryOutboxRepository outbox;
    private NotificationAuditRecorder recorder;
    private ChannelProvider smsProvider;

    @BeforeEach
    void setUp() {
        logs = new InMemoryLogRepository();
        attempts = new InMemoryAttemptRepository();
        outbox = new InMemoryOutboxRepository();
        NmsProperties props = new NmsProperties();
        CommunicationAuditPublisher publisher = new CommunicationAuditPublisher(outbox,
                new ObjectMapper().registerModule(new JavaTimeModule()), props, CLOCK);
        recorder = new NotificationAuditRecorder(logs, attempts, publisher, props, CLOCK);
        smsProvider = new ChannelProvider() {
            @Override
            public NotificationChannel getChannel() {
                return NotificationChannel.SMS;
            }

            @Override
            public boolean send(String recipient, String subject, String body, String templateId) {
                return true;
            }
        };
    }

    private NotificationRequest request() {
        NotificationRequest r = new NotificationRequest();
        r.setMessageId("msg-1");
        r.setCorrelationId("corr-1");
        r.setEvent("OTP_SENT");
        r.setSourceService("rider-uam");
        r.setTriggeringUserId("u-1");
        r.setEntityType("USER");
        r.setEntityId("u-1");
        return r;
    }

    private AttemptResult attempt(int n, boolean ok, String provider, String providerMsgId, String code, String msg) {
        return new AttemptResult(n, ok, provider, providerMsgId, null, code, msg, T0.plusSeconds(n), 40L);
    }

    @Test
    void aRetriedSendWritesOneSummaryRowAndOneRecordPerAttempt() {
        ChannelSendOutcome outcome = new ChannelSendOutcome(true, null, 3, List.of(
                attempt(1, false, "digimile", null, "DIGIMILE_1707", "bad"),
                attempt(2, false, "digimile", null, "DIGIMILE_1707", "bad"),
                attempt(3, true, "digimile", "abc123", null, null)));

        recorder.record(request(), smsProvider, "9876543210", "Your OTP is 123456", outcome);

        assertThat(logs.all()).hasSize(1);
        NotificationLog log = logs.all().get(0);
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(log.getRetryCount()).isEqualTo(2);
        assertThat(log.getProviderName()).isEqualTo("digimile");
        assertThat(log.getProviderMessageId()).isEqualTo("abc123");
        assertThat(log.getSourceService()).isEqualTo("rider-uam");
        assertThat(log.getEventCode()).isEqualTo("OTP_SENT");
        assertThat(log.getRenderedBody()).isNull();

        List<NotificationAttempt> saved = attempts.all();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(NotificationAttempt::getAttemptNumber).containsExactly(1, 2, 3);
        assertThat(saved).extracting(NotificationAttempt::getStatus)
                .containsExactly(DeliveryStatus.FAILED, DeliveryStatus.FAILED, DeliveryStatus.SENT);
        assertThat(saved).allSatisfy(a -> {
            assertThat(a.getLogId()).isEqualTo(log.getId());
            assertThat(a.getMessageId()).isEqualTo("msg-1");
            assertThat(a.getCorrelationId()).isEqualTo("corr-1");
            assertThat(a.getSourceService()).isEqualTo("rider-uam");
            assertThat(a.getTriggeringUserId()).isEqualTo("u-1");
            assertThat(a.getRecipient()).isEqualTo("******3210");
            assertThat(a.getLatencyMs()).isEqualTo(40L);
        });
        assertThat(saved.get(2).getProviderMessageId()).isEqualTo("abc123");
        assertThat(saved.get(0).getErrorCode()).isEqualTo("DIGIMILE_1707");
        assertThat(saved.get(2).getErrorCode()).isNull();
        assertThat(outbox.all()).hasSize(3);
    }

    @Test
    void aTotalFailureIsRecordedAsFailedWithTheReason() {
        ChannelSendOutcome outcome = new ChannelSendOutcome(false, "Provider circuit open", 0, List.of(
                new AttemptResult(1, false, null, null, null, "CIRCUIT_OPEN", "Provider circuit open", T0, 0L)));

        recorder.record(request(), smsProvider, "9876543210", "body", outcome);

        NotificationLog log = logs.all().get(0);
        assertThat(log.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getErrorMessage()).isEqualTo("Provider circuit open");
        assertThat(attempts.all()).hasSize(1);
        assertThat(attempts.all().get(0).getErrorCode()).isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void callersThatPassNoAttemptDetailStillGetOneAttempt() {
        recorder.record(request(), smsProvider, "9876543210", "body", new ChannelSendOutcome(false, "nope", 2));

        assertThat(attempts.all()).hasSize(1);
        assertThat(attempts.all().get(0).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempts.all().get(0).getErrorMessage()).isEqualTo("nope");
    }

    @Test
    void aRequestWithoutOriginStillWorks() {
        NotificationRequest bare = new NotificationRequest();
        bare.setEvent("RIDE_BOOKED");

        recorder.record(bare, smsProvider, "9876543210", "body",
                new ChannelSendOutcome(true, null, 1, List.of(attempt(1, true, "twilio", "SM1", null, null))));

        assertThat(logs.all().get(0).getSourceService()).isNull();
        assertThat(attempts.all().get(0).getSourceService()).isNull();
        assertThat(attempts.all().get(0).getEventCode()).isEqualTo("RIDE_BOOKED");
    }

    @Test
    void aFailingAttemptStoreDoesNotFailTheSend() {
        attempts.failNextSave = new IllegalStateException("db down");

        assertThatCode(() -> recorder.record(request(), smsProvider, "9876543210", "body",
                new ChannelSendOutcome(true, null, 1, List.of(attempt(1, true, "twilio", "SM1", null, null)))))
                .doesNotThrowAnyException();
        assertThat(logs.all()).hasSize(1);
    }

    @Test
    void theRenderedBodyIsOnlyKeptWhenConfigured() {
        NmsProperties props = new NmsProperties();
        props.getAudit().setStoreRenderedBody(true);
        NotificationAuditRecorder withBody = new NotificationAuditRecorder(logs, attempts,
                new CommunicationAuditPublisher(outbox, new ObjectMapper().registerModule(new JavaTimeModule()), props, CLOCK),
                props, CLOCK);

        withBody.record(request(), smsProvider, "9876543210", "Your OTP is 123456",
                new ChannelSendOutcome(true, null, 1, List.of(attempt(1, true, "twilio", "SM1", null, null))));

        assertThat(logs.all().get(0).getRenderedBody()).isEqualTo("Your OTP is 123456");
        // ...but the attempt and the audit event never carry it.
        assertThat(outbox.all().get(0).getPayload()).doesNotContain("123456");
    }
}
