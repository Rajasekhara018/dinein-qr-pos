package com.heuristiq.notification.application.usecase;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.in.TemplateUseCase;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.application.security.NotificationTenantContext;
import com.heuristiq.notification.application.usecase.support.ChannelResilienceDecorator;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.application.usecase.support.NotificationAuditRecorder;
import com.heuristiq.notification.application.usecase.support.NotificationDispatchMetrics;
import com.heuristiq.notification.application.usecase.support.SmsRecipientResolver;
import com.heuristiq.notification.application.usecase.support.TemplateRenderer;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationMessage;
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

/** The organization stamped on the message, log and attempts of a dispatch, and handed to the channel provider. */
class NotificationTenantStampingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryLogRepository logs;
    private InMemoryAttemptRepository attempts;
    private final List<NotificationMessage> storedMessages = new ArrayList<>();
    private final List<String> tenantSeenByProvider = new ArrayList<>();
    private NotificationDispatchService dispatch;

    @BeforeEach
    void setUp() {
        logs = new InMemoryLogRepository();
        attempts = new InMemoryAttemptRepository();
        InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
        NmsProperties props = new NmsProperties();

        INotificationMessageRepository messages = mock(INotificationMessageRepository.class);
        when(messages.insert(any())).thenAnswer(i -> {
            storedMessages.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(messages.findByMessageId(anyString())).thenAnswer(i -> storedMessages.stream().findFirst());
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
                tenantSeenByProvider.add(NotificationTenantContext.current());
                return true;
            }
        };

        ChannelResilienceDecorator decorator = new ChannelResilienceDecorator(
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(50).minimumNumberOfCalls(50).build()),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ofMillis(1)).build()),
                BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(10).build()), CLOCK);
        CommunicationAuditPublisher publisher = new CommunicationAuditPublisher(outbox,
                new ObjectMapper().registerModule(new JavaTimeModule()), props, CLOCK);
        dispatch = new NotificationDispatchService(templates, new TemplateRenderer(),
                new NotificationMessageService(messages, props), Runnable::run, decorator,
                new NotificationAuditRecorder(logs, attempts, publisher, props, CLOCK),
                new com.heuristiq.notification.application.usecase.support.OrganizationVariables(tenant -> Optional.empty(), "RealBoom"),
                new NotificationDispatchMetrics(new SimpleMeterRegistry()), List.of(sms),
                List.of(new SmsRecipientResolver()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String tenantId, boolean internal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal(internal ? "internal-service" : "u-1", tenantId, internal), null, List.of()));
    }

    private static NotificationRequest request(String bodyTenantId) {
        NotificationRequest r = new NotificationRequest();
        r.setMessageId("msg-1");
        r.setCorrelationId("corr-1");
        r.setEvent("otp_sent");
        r.setToPhone("9876543210");
        r.setVariables(Map.of("otp", "123456"));
        r.setTenantId(bodyTenantId);
        return r;
    }

    private void assertEverythingStampedWith(String expected) {
        assertThat(storedMessages).singleElement().satisfies(m -> assertThat(m.getTenantId()).isEqualTo(expected));
        assertThat(logs.all()).singleElement().satisfies(l -> assertThat(l.getTenantId()).isEqualTo(expected));
        assertThat(attempts.all()).isNotEmpty().allSatisfy(a -> assertThat(a.getTenantId()).isEqualTo(expected));
        assertThat(tenantSeenByProvider).containsExactly(expected);
        assertThat(NotificationTenantContext.current()).isNull();
    }

    @Test
    void aUsersDispatchIsStampedWithTheirOrganizationOnMessageLogAttemptsAndProvider() {
        signInAs("org-a", false);

        dispatch.dispatch(request(null));

        assertEverythingStampedWith("org-a");
    }

    @Test
    void aUsersRequestBodyCannotChooseAnotherOrganization() {
        signInAs("org-a", false);

        dispatch.dispatch(request("org-b"));

        assertEverythingStampedWith("org-a");
    }

    @Test
    void aUserWithoutAnOrganizationStoresNoneEvenIfTheBodyNamesOne() {
        signInAs(null, false);

        dispatch.dispatch(request("org-b"));

        assertEverythingStampedWith(null);
    }

    @Test
    void anInternalCallerMayNameTheOrganizationInTheRequest() {
        signInAs(null, true);

        dispatch.dispatch(request("org-b"));

        assertEverythingStampedWith("org-b");
    }

    @Test
    void workWithNoCallerSuchAsKafkaOrTheSchedulerKeepsTheRequestsOrganization() {
        dispatch.dispatch(request("org-c"));

        assertEverythingStampedWith("org-c");
    }

    @Test
    void workWithNoCallerAndNoOrganizationStoresNone() {
        dispatch.dispatch(request(null));

        assertEverythingStampedWith(null);
    }
}
