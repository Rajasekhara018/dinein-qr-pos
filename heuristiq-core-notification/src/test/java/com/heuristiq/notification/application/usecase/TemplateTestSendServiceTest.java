package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.TemplateTestSendRequest;
import com.heuristiq.notification.application.dto.TemplateTestSendResponse;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.application.usecase.support.OrganizationVariables;
import com.heuristiq.notification.application.usecase.support.TemplateRenderer;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.shared.NmsTooManyRequestsException;

class TemplateTestSendServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
    private ChannelProvider email;
    private ChannelProvider sms;
    private TemplateTestSendService service;

    @BeforeEach
    void setUp() {
        email = mock(ChannelProvider.class);
        when(email.getChannel()).thenReturn(NotificationChannel.EMAIL);
        when(email.sendDetailed(any(), any(), any(), any())).thenReturn(ProviderSendResult.ok("smtp", "m-1", null));
        sms = mock(ChannelProvider.class);
        when(sms.getChannel()).thenReturn(NotificationChannel.SMS);
        when(sms.sendDetailed(any(), any(), any(), any())).thenReturn(ProviderSendResult.ok("digimile", "m-2", null));

        NmsProperties properties = new NmsProperties();
        properties.getTemplateTest().setMaxPerHour(2);
        OrganizationVariables variables = new OrganizationVariables(tenant -> java.util.Optional.empty(), "Acme");
        service = new TemplateTestSendService(new TemplateRenderer(), variables, List.of(email, sms), properties, clock);
    }

    private TemplateTestSendRequest emailRequest(String recipient) {
        return new TemplateTestSendRequest(NotificationChannel.EMAIL, recipient, "Hello {{name}}",
                "<p>Your code is {{otp}} from {{organizationName}}</p>", Map.of("name", "Asha", "otp", "482913"));
    }

    @Test
    void emailIsRenderedAndMarkedAsATest() {
        TemplateTestSendResponse response = service.send("user-1", null, emailRequest("ops@example.com"));

        assertThat(response.sent()).isTrue();
        assertThat(response.provider()).isEqualTo("smtp");
        verify(email).sendDetailed(eq("ops@example.com"), eq("[TEST] Hello Asha"),
                eq("<p>Your code is 482913 from Acme</p>"), isNull());
    }

    @Test
    void smsGetsTheMarkerInTheBodyAndAPhoneWithoutFormatting() {
        service.send("user-1", null, new TemplateTestSendRequest(NotificationChannel.SMS, "+91 98765-43210",
                null, "OTP {{otp}}", Map.of("otp", "1111")));

        verify(sms).sendDetailed(eq("+919876543210"), isNull(), eq("[TEST] OTP 1111"), isNull());
    }

    @Test
    void aFailingProviderIsReportedNotThrown() {
        when(email.sendDetailed(any(), any(), any(), any()))
                .thenReturn(ProviderSendResult.failed("smtp", "PROVIDER_REJECTED", "mailbox full"));

        TemplateTestSendResponse response = service.send("user-1", null, emailRequest("ops@example.com"));

        assertThat(response.sent()).isFalse();
        assertThat(response.errorCode()).isEqualTo("PROVIDER_REJECTED");
        assertThat(response.message()).isEqualTo("mailbox full");
    }

    @Test
    void rejectsChannelsThatNeedADeviceInsteadOfATypedRecipient() {
        assertThatThrownBy(() -> service.send("user-1", null, new TemplateTestSendRequest(
                NotificationChannel.FCM_PUSH, "token", "t", "b", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRecipientsBeforeSending() {
        assertThatThrownBy(() -> service.send("user-1", null, emailRequest("not-an-email")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send("user-1", null, emailRequest("a@b.com, c@d.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send("user-1", null, new TemplateTestSendRequest(
                NotificationChannel.SMS, "12ab", null, "x", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(email, never()).sendDetailed(any(), any(), any(), any());
        verify(sms, never()).sendDetailed(any(), any(), any(), any());
    }

    @Test
    void aCallerIsLimitedButOthersAreNot() {
        service.send("user-1", null, emailRequest("ops@example.com"));
        service.send("user-1", null, emailRequest("ops@example.com"));

        assertThatThrownBy(() -> service.send("user-1", null, emailRequest("ops@example.com")))
                .isInstanceOf(NmsTooManyRequestsException.class);
        assertThat(service.send("user-2", null, emailRequest("ops@example.com")).sent()).isTrue();
    }
}
