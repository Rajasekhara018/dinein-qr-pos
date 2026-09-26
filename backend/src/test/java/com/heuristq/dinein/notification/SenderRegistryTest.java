package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationProperties.Email;
import com.heuristq.dinein.notification.NotificationProperties.InApp;
import com.heuristq.dinein.notification.NotificationProperties.Push;
import com.heuristq.dinein.notification.NotificationProperties.Sms;
import com.heuristq.dinein.notification.sms.LogSmsSender;
import com.heuristq.dinein.notification.sms.SmsSender;
import com.heuristq.dinein.notification.sms.SmsSenderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SenderRegistryTest {

    private final SmsSender twilio = twilio();

    @Test
    void selectsConfiguredProviderCaseInsensitively() {
        SmsSenderRegistry registry = new SmsSenderRegistry(List.of(new LogSmsSender(), twilio), props(" twilio "));

        assertThat(registry.activeProvider()).isEqualTo("TWILIO");
        assertThat(registry.active()).isSameAs(twilio);
    }

    @Test
    void blankProviderFallsBackToLog() {
        SmsSenderRegistry registry = new SmsSenderRegistry(List.of(new LogSmsSender(), twilio), props(""));

        assertThat(registry.activeProvider()).isEqualTo(SenderRegistry.LOG);
        assertThat(registry.active()).isInstanceOf(LogSmsSender.class);
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> new SmsSenderRegistry(List.of(new LogSmsSender(), twilio), props("SMSWIZ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMSWIZ");
    }

    private static SmsSender twilio() {
        SmsSender sender = mock(SmsSender.class);
        when(sender.provider()).thenReturn("TWILIO");
        return sender;
    }

    private static NotificationProperties props(String smsProvider) {
        return new NotificationProperties(new Email(true, "LOG", null), new Sms(true, smsProvider, "91"),
                new Push(true, "LOG"), new InApp(true));
    }
}
