package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.DeliveryReceipt;
import com.heuristiq.notification.application.dto.NotificationDispatchResult;
import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.dto.ReceiptOutcome;
import com.heuristiq.notification.application.port.in.TemplateUseCase;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.application.usecase.support.ChannelResilienceDecorator;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.application.usecase.support.NotificationAuditRecorder;
import com.heuristiq.notification.application.usecase.support.NotificationDispatchMetrics;
import com.heuristiq.notification.application.usecase.support.SmsRecipientResolver;
import com.heuristiq.notification.application.usecase.support.TemplateRenderer;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationMessage;
import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.repository.INotificationMessageRepository;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;
import com.heuristiq.notification.testsupport.InMemoryLogRepository;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The whole send path with in-memory stores: origin, per-attempt records, audit events, receipts. */
class NotificationDispatchFlowTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryLogRepository logs;
    private InMemoryAttemptRepository attempts;
    private InMemoryOutboxRepository outbox;
    private INotificationMessageRepository messages;
    private NotificationDispatchService dispatch;
    private DeliveryReceiptService receipts;
    private ProviderBehaviour behaviour;

    /** Programmable provider. */
    private static final class ProviderBehaviour {
        final AtomicInteger calls = new AtomicInteger();
        java.util.function.IntFunction<ProviderSendResult> perCall = n -> ProviderSendResult.ok("digimile", "pm-1", "1701");
    }

    @BeforeEach
    void setUp() {
        logs = new InMemoryLogRepository();
        attempts = new InMemoryAttemptRepository();
        outbox = new InMemoryOutboxRepository();
        behaviour = new ProviderBehaviour();
        NmsProperties props = new NmsProperties();

        messages = mock(INotificationMessageRepository.class);
        java.util.concurrent.atomic.AtomicReference<NotificationMessage> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(messages.insert(any())).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(messages.findByMessageId(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateUseCase templates = mock(TemplateUseCase.class);
        NotificationTemplate template = new NotificationTemplate();
        template.setEvent("OTP_SENT");
        template.setChannel(NotificationChannel.SMS);
        template.setBody("Your OTP is {{otp}}");
        when(templates.findByEvent("OTP_SENT")).thenReturn(List.of(template));
        when(templates.findForTenant(eq("OTP_SENT"), eq(NotificationChannel.SMS), any())).thenReturn(Optional.of(template));

        ChannelProvider sms = new ChannelProvider() {
            @Override
            public NotificationChannel getChannel() {
                return NotificationChannel.SMS;
            }

            @Override
            public boolean send(String recipient, String subject, String body, String templateId) {
                return sendDetailed(recipient, subject, body, templateId).success();
            }

            @Override
            public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
                return behaviour.perCall.apply(behaviour.calls.incrementAndGet());
            }
        };

        ChannelResilienceDecorator decorator = new ChannelResilienceDecorator(
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(50).minimumNumberOfCalls(50).build()),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build()),
                BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(10).build()), CLOCK);
        CommunicationAuditPublisher publisher = new CommunicationAuditPublisher(outbox,
                new ObjectMapper().registerModule(new JavaTimeModule()), props, CLOCK);
        dispatch = new NotificationDispatchService(templates, new TemplateRenderer(),
                new NotificationMessageService(messages, props), Runnable::run, decorator,
                new NotificationAuditRecorder(logs, attempts, publisher, props, CLOCK),
                new com.heuristiq.notification.application.usecase.support.OrganizationVariables(tenant -> Optional.empty(), "RealBoom"),
                new NotificationDispatchMetrics(new SimpleMeterRegistry()), List.of(sms),
                List.of(new SmsRecipientResolver()));
        receipts = new DeliveryReceiptService(attempts, logs, publisher, CLOCK);
    }

    private NotificationRequest otpRequest() {
        NotificationRequest r = new NotificationRequest();
        r.setMessageId("msg-1");
        r.setCorrelationId("corr-1");
        r.setEvent("otp_sent");
        r.setToPhone("9876543210");
        r.setVariables(Map.of("otp", "123456"));
        r.setSourceService("rider-uam");
        r.setTriggeringUserId("u-1");
        r.setEntityType("USER");
        r.setEntityId("u-1");
        return r;
    }

    @Test
    void aProviderThatKeepsFailingLeavesAFullTrailOfFailedAttempts() {
        behaviour.perCall = n -> ProviderSendResult.failed("digimile", "DIGIMILE_1707", "Digimile response code 1707");

        List<NotificationDispatchResult> results = dispatch.dispatch(otpRequest());

        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isFalse());
        assertThat(behaviour.calls.get()).isEqualTo(3);
        assertThat(logs.all()).singleElement().satisfies(log -> {
            assertThat(log.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(log.getRetryCount()).isEqualTo(2);
            assertThat(log.getSourceService()).isEqualTo("rider-uam");
            assertThat(log.getEntityId()).isEqualTo("u-1");
        });
        assertThat(attempts.all()).hasSize(3).allSatisfy(a -> {
            assertThat(a.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(a.getErrorCode()).isEqualTo("DIGIMILE_1707");
            assertThat(a.getRecipient()).isEqualTo("******3210");
            assertThat(a.getMessageId()).isEqualTo("msg-1");
            assertThat(a.getCorrelationId()).isEqualTo("corr-1");
        });
        assertThat(outbox.all()).hasSize(3);
        assertThat(outbox.all()).allSatisfy(e -> assertThat(e.getPayload())
                .contains("\"outcome\":\"FAILURE\"").doesNotContain("123456").doesNotContain("9876543210"));
        ArgumentCaptor<NotificationMessage> saved = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationMessageStatus.FAILED);
    }

    @Test
    void aRetryThatSucceedsKeepsTheFailedAttemptAndAReceiptThenCompletesTheStory() {
        behaviour.perCall = n -> n == 1
                ? ProviderSendResult.failed("digimile", ProviderSendResult.CODE_EXCEPTION, "timeout")
                : ProviderSendResult.ok("digimile", "pm-77", "1701");

        List<NotificationDispatchResult> results = dispatch.dispatch(otpRequest());

        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(attempts.all()).extracting(NotificationAttempt::getStatus)
                .containsExactly(DeliveryStatus.FAILED, DeliveryStatus.SENT);
        assertThat(logs.all().get(0).getProviderMessageId()).isEqualTo("pm-77");

        ReceiptOutcome outcome = receipts.apply(new DeliveryReceipt("digimile", "pm-77", DeliveryStatus.DELIVERED,
                "DELIVRD", null, null, null));

        assertThat(outcome).isEqualTo(ReceiptOutcome.APPLIED);
        assertThat(attempts.all()).extracting(NotificationAttempt::getStatus)
                .containsExactly(DeliveryStatus.FAILED, DeliveryStatus.DELIVERED);
        assertThat(logs.all().get(0).getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        // 2 attempt outcomes + 1 DELIVERED update
        assertThat(outbox.all()).hasSize(3);
        assertThat(outbox.all().get(2).getEventId()).endsWith("_DELIVERED");
    }

    @Test
    void theOriginIsStoredOnTheMessage() {
        dispatch.dispatch(otpRequest());

        ArgumentCaptor<NotificationMessage> inserted = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(messages).insert(inserted.capture());
        NotificationMessage m = inserted.getValue();
        assertThat(m.getSourceService()).isEqualTo("rider-uam");
        assertThat(m.getEventCode()).isEqualTo("OTP_SENT");
        assertThat(m.getTriggeringUserId()).isEqualTo("u-1");
        assertThat(m.getEntityType()).isEqualTo("USER");
        assertThat(m.getEntityId()).isEqualTo("u-1");
    }

    @Test
    void aProducerThatSendsNoOriginStillDispatches() {
        NotificationRequest legacy = new NotificationRequest();
        legacy.setMessageId("msg-legacy");
        legacy.setEvent("OTP_SENT");
        legacy.setToPhone("9876543210");

        List<NotificationDispatchResult> results = dispatch.dispatch(legacy);

        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(attempts.all()).singleElement().satisfies(a -> assertThat(a.getSourceService()).isNull());
    }

    @Test
    void anUnreachableAuditStoreNeverBreaksTheSend() {
        outbox.failInserts = new IllegalStateException("outbox db down");

        List<NotificationDispatchResult> results = dispatch.dispatch(otpRequest());

        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(attempts.all()).hasSize(1);
    }

    @Test
    void anUnreachableAttemptStoreNeverBreaksTheSend() {
        attempts.failNextSave = new IllegalStateException("attempt db down");

        List<NotificationDispatchResult> results = dispatch.dispatch(otpRequest());

        assertThat(results).singleElement().satisfies(r -> assertThat(r.success()).isTrue());
        assertThat(logs.all()).hasSize(1);
    }

    @Test
    void aDuplicateMessageIdIsIgnoredWithoutNewAttempts() {
        NotificationMessage existing = new NotificationMessage();
        existing.setMessageId("msg-1");
        existing.setStatus(NotificationMessageStatus.SENT);
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        when(messages.insert(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(messages.findByMessageId(anyString())).thenReturn(Optional.of(existing));
        when(messages.findFirstByMessageIdAndStatusAndUpdatedAtBefore(anyString(), any(), any())).thenReturn(Optional.empty());

        List<NotificationDispatchResult> results = dispatch.dispatch(otpRequest());

        assertThat(results).singleElement().satisfies(r -> assertThat(r.error()).contains("Duplicate"));
        assertThat(behaviour.calls.get()).isZero();
        assertThat(attempts.all()).isEmpty();
        assertThat(outbox.all()).isEmpty();
    }
}
