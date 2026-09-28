package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationProperties.Email;
import com.heuristq.dinein.notification.NotificationProperties.InApp;
import com.heuristq.dinein.notification.NotificationProperties.Push;
import com.heuristq.dinein.notification.NotificationProperties.Sms;
import com.heuristq.dinein.notification.NotificationRequest.PushTarget;
import com.heuristq.dinein.notification.NotificationRequest.Recipients;
import com.heuristq.dinein.notification.NotificationTemplates.Message;
import com.heuristq.dinein.notification.domain.DeliveryStatus;
import com.heuristq.dinein.notification.domain.NotificationLogEntity;
import com.heuristq.dinein.notification.domain.NotificationLogRepository;
import com.heuristq.dinein.notification.email.EmailSender;
import com.heuristq.dinein.notification.email.EmailSenderRegistry;
import com.heuristq.dinein.notification.push.InvalidPushTokenException;
import com.heuristq.dinein.notification.push.PushSender;
import com.heuristq.dinein.notification.push.PushSenderRegistry;
import com.heuristq.dinein.notification.sms.SmsMessage;
import com.heuristq.dinein.notification.sms.SmsSender;
import com.heuristq.dinein.notification.sms.SmsSenderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private final SmsSender sms = mock(SmsSender.class);
    private final EmailSender email = mock(EmailSender.class);
    private final PushSender push = mock(PushSender.class);
    private final NotificationTemplates templates = mock(NotificationTemplates.class);
    private final InAppNotificationService inApp = mock(InAppNotificationService.class);
    private final PushSubscriptionService subscriptions = mock(PushSubscriptionService.class);
    private final NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(sms.provider()).thenReturn("TWILIO");
        when(email.provider()).thenReturn("SMTP");
        when(push.provider()).thenReturn("FCM");
        when(templates.restaurantName(any())).thenReturn("Spice Garden");
        when(templates.render(any(), any(), any(), anyString())).thenReturn(new Message("Subject", "Body", "/x"));
        dispatcher = dispatcher(true);
    }

    @Test
    void failingSenderIsLoggedAsFailedAndDoesNotThrow() {
        doThrow(new DeliveryException("Twilio HTTP 500")).when(sms).send(any());

        assertThatCode(() -> dispatcher.dispatch(request(new Recipients(null, null, List.of("9876543210"), null))))
                .doesNotThrowAnyException();

        NotificationLogEntity entry = savedLogs().get(0);
        assertThat(entry.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(entry.getProvider()).isEqualTo("TWILIO");
        assertThat(entry.getRecipientMasked()).isEqualTo("******3210");
        assertThat(entry.getError()).contains("Twilio HTTP 500");
        assertThat(entry.getRelatedOrderId()).isEqualTo(42L);
    }

    @Test
    void oneFailureDoesNotStopOtherRecipients() {
        doThrow(new RuntimeException("boom")).when(email).send(any());

        dispatcher.dispatch(request(new Recipients(null, List.of("owner@example.com"), List.of("9876543210"), null)));

        ArgumentCaptor<SmsMessage> sent = ArgumentCaptor.forClass(SmsMessage.class);
        verify(sms).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("+919876543210");
        assertThat(savedLogs()).extracting(NotificationLogEntity::getStatus)
                .containsExactly(DeliveryStatus.FAILED, DeliveryStatus.SENT);
    }

    @Test
    void unregisteredPushTokenDeactivatesSubscription() {
        doThrow(new InvalidPushTokenException("UNREGISTERED")).when(push).send(any());

        dispatcher.dispatch(request(new Recipients(null, null, null,
                List.of(new PushTarget(7L, "FCM", "token-abcdefghijklmnop")))));

        verify(subscriptions).deactivate(7L);
        verify(subscriptions, never()).markUsed(any());
        assertThat(savedLogs().get(0).getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void disabledChannelIsSkipped() {
        dispatcher = dispatcher(false);

        dispatcher.dispatch(request(new Recipients(null, null, List.of("9876543210"), null)));

        verify(sms, never()).send(any());
        assertThat(savedLogs().get(0).getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void logWriteFailureIsSwallowed() {
        when(logRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> dispatcher.dispatch(request(new Recipients(null, null, List.of("9876543210"), null))))
                .doesNotThrowAnyException();
        verify(sms, times(1)).send(any());
    }

    private NotificationDispatcher dispatcher(boolean smsEnabled) {
        NotificationProperties props = new NotificationProperties(new Email(true, "SMTP", "noreply@example.com"),
                new Sms(smsEnabled, "TWILIO", "91"), new Push(true, "FCM"), new InApp(true));
        return new NotificationDispatcher(props, templates, new EmailSenderRegistry(List.of(email), props),
                new SmsSenderRegistry(List.of(sms), props), new PushSenderRegistry(List.of(push), props), inApp,
                subscriptions, logRepository);
    }

    private static NotificationRequest request(Recipients recipients) {
        return new NotificationRequest(NotificationEvent.ORDER_READY, 42L, 1L, Map.of("token", "17"), recipients);
    }

    private List<NotificationLogEntity> savedLogs() {
        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }
}
