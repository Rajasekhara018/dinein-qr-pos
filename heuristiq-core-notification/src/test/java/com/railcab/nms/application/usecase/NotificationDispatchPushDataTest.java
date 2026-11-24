package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.railcab.nms.application.dto.NotificationDispatchResult;
import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.in.TemplateUseCase;
import com.railcab.nms.application.port.out.ChannelProvider;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.application.port.out.RecipientResolver;
import com.railcab.nms.application.usecase.support.ChannelResilienceDecorator;
import com.railcab.nms.application.usecase.support.CommunicationAuditPublisher;
import com.railcab.nms.application.usecase.support.NotificationAuditRecorder;
import com.railcab.nms.application.usecase.support.NotificationDispatchMetrics;
import com.railcab.nms.application.usecase.support.OrganizationVariables;
import com.railcab.nms.application.usecase.support.TemplateRenderer;
import com.railcab.nms.application.usecase.support.VoiceRecipientResolver;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.testsupport.InMemoryAttemptRepository;
import com.railcab.nms.testsupport.InMemoryLogRepository;
import com.railcab.nms.testsupport.InMemoryOutboxRepository;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The dispatcher hands every provider the push data map built from the request (push data contract). */
class NotificationDispatchPushDataTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);

    private final Map<NotificationChannel, Map<String, String>> received = new ConcurrentHashMap<>();
    private final AtomicReference<String> voiceRecipient = new AtomicReference<>();
    private NotificationDispatchService dispatch;

    private ChannelProvider capturing(NotificationChannel channel) {
        return new ChannelProvider() {
            @Override
            public NotificationChannel getChannel() {
                return channel;
            }

            @Override
            public boolean send(String recipient, String subject, String body, String templateId) {
                return true;
            }

            @Override
            public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId,
                    Map<String, String> data) {
                received.put(channel, data);
                if (channel == NotificationChannel.VOICE) {
                    voiceRecipient.set(recipient);
                }
                return ProviderSendResult.ok("fake", "pm-" + channel, "accepted");
            }
        };
    }

    private static NotificationTemplate template(String event, NotificationChannel channel) {
        NotificationTemplate template = new NotificationTemplate();
        template.setEvent(event);
        template.setChannel(channel);
        template.setSubject("Payment due");
        template.setBody("Please pay {{finalFare}}");
        return template;
    }

    @BeforeEach
    void setUp() {
        NmsProperties props = new NmsProperties();
        INotificationMessageRepository messages = mock(INotificationMessageRepository.class);
        AtomicReference<NotificationMessage> stored = new AtomicReference<>();
        when(messages.insert(any())).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(messages.findByMessageId(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateUseCase templates = mock(TemplateUseCase.class);
        for (NotificationChannel channel : List.of(NotificationChannel.FCM_PUSH, NotificationChannel.VOICE)) {
            when(templates.findForTenant(eq("PAYMENT_PENDING"), eq(channel), any()))
                    .thenReturn(Optional.of(template("PAYMENT_PENDING", channel)));
        }
        RecipientResolver push = new RecipientResolver() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.FCM_PUSH;
            }

            @Override
            public List<String> resolve(NotificationRequest request) {
                return request.getFcmTokens() == null ? List.of() : request.getFcmTokens();
            }
        };
        ChannelResilienceDecorator decorator = new ChannelResilienceDecorator(
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(50).minimumNumberOfCalls(50).build()),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ofMillis(1)).build()),
                BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(10).build()), CLOCK);
        CommunicationAuditPublisher publisher = new CommunicationAuditPublisher(new InMemoryOutboxRepository(),
                new ObjectMapper().registerModule(new JavaTimeModule()), props, CLOCK);
        dispatch = new NotificationDispatchService(templates, new TemplateRenderer(),
                new NotificationMessageService(messages, props), Runnable::run, decorator,
                new NotificationAuditRecorder(new InMemoryLogRepository(), new InMemoryAttemptRepository(), publisher, props, CLOCK),
                new OrganizationVariables(tenant -> Optional.empty(), "RealBoom"),
                new NotificationDispatchMetrics(new SimpleMeterRegistry()),
                List.of(capturing(NotificationChannel.FCM_PUSH), capturing(NotificationChannel.VOICE)),
                List.of(push, new VoiceRecipientResolver()));
    }

    @Test
    void aRidePushCarriesTheContractDataAndVoiceAlsoTheMessageId() {
        NotificationRequest request = new NotificationRequest();
        request.setMessageId("msg-7");
        request.setEvent("payment_pending");
        request.setEntityType("RIDE");
        request.setEntityId("ride-7");
        request.setFcmTokens(List.of("tok-1234567890"));
        request.setToPhone("+919876543210");
        request.setChannels(List.of(NotificationChannel.FCM_PUSH, NotificationChannel.VOICE));
        request.setVariables(Map.of("finalFare", "250.00", "otp", "4321"));

        List<NotificationDispatchResult> results = dispatch.dispatch(request);

        assertThat(results).allSatisfy(r -> assertThat(r.success()).isTrue());
        assertThat(received.get(NotificationChannel.FCM_PUSH))
                .containsEntry("type", "PAYMENT_PENDING")
                .containsEntry("rideId", "ride-7")
                .containsEntry("entityType", "RIDE")
                .containsEntry("finalFare", "250.00")
                .containsEntry("url", "/notifications")
                .doesNotContainKey("otp")
                .doesNotContainKey("messageId");
        assertThat(received.get(NotificationChannel.VOICE)).containsEntry("messageId", "msg-7").containsEntry("rideId", "ride-7");
        assertThat(voiceRecipient.get()).isEqualTo("+919876543210");
    }

    @Test
    void voiceWithoutAPhoneIsSkipped() {
        NotificationRequest request = new NotificationRequest();
        request.setMessageId("msg-8");
        request.setEvent("PAYMENT_PENDING");
        request.setChannels(List.of(NotificationChannel.VOICE));

        List<NotificationDispatchResult> results = dispatch.dispatch(request);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.success()).isFalse();
            assertThat(r.error()).isEqualTo("No recipients provided");
        });
        assertThat(received).doesNotContainKey(NotificationChannel.VOICE);
    }
}
