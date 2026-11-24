package com.railcab.nms.adapters.out.channel.fcm.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.domain.repository.IDeviceTokenRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushProviderTest {

    private static final String TOKEN = "fJ3xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final IDeviceTokenRepository tokens = mock(IDeviceTokenRepository.class);
    private final FirebasePushProvider provider = new FirebasePushProvider();

    FirebasePushProviderTest() {
        ReflectionTestUtils.setField(provider, "firebaseMessaging", messaging);
        ReflectionTestUtils.setField(provider, "deviceTokenRepository", tokens);
    }

    private static FirebaseMessagingException failing(MessagingErrorCode code) {
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(code);
        return failure;
    }

    @Test
    void aTokenFirebaseSaysIsUnregisteredIsRemovedSoItIsNeverRetried() throws Exception {
        FirebaseMessagingException failure = failing(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(failure);

        assertFalse(provider.send(TOKEN, "title", "body"));

        verify(tokens).unregisterToken(TOKEN);
    }

    @Test
    void transientOrPayloadFailuresNeverDeleteTokens() throws Exception {
        FirebaseMessagingException failure = failing(MessagingErrorCode.INTERNAL);
        when(messaging.send(any(Message.class))).thenThrow(failure);
        assertFalse(provider.send(TOKEN, "title", "body"));

        FirebaseMessagingException invalid = failing(MessagingErrorCode.INVALID_ARGUMENT);
        when(messaging.send(any(Message.class))).thenThrow(invalid);
        assertFalse(provider.send(TOKEN, "title", "body"));

        verify(tokens, never()).unregisterToken(anyString());
    }

    @Test
    void aSuccessfulSendKeepsTheToken() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");

        assertTrue(provider.send(TOKEN, "title", "body"));

        verify(tokens, never()).unregisterToken(anyString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> dataOf(Message message) {
        return (Map<String, String>) ReflectionTestUtils.getField(message, "data");
    }

    private static Object android(Message message, String field) {
        Object config = ReflectionTestUtils.getField(message, "androidConfig");
        return ReflectionTestUtils.getField(config, field);
    }

    @Test
    void aRidePushCarriesItsDataHighPriorityAndTheRideUpdatesChannel() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/2");

        ProviderSendResult result = provider.sendDetailed(TOKEN, "title", "body",
                Map.of("type", "PAYMENT_PENDING", "entityType", "RIDE", "rideId", "ride-1", "url", "/elsewhere"));

        assertTrue(result.success());
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(sent.capture());
        Map<String, String> data = dataOf(sent.getValue());
        assertEquals("PAYMENT_PENDING", data.get("type"));
        assertEquals("ride-1", data.get("rideId"));
        assertEquals("/notifications", data.get("url"));
        assertEquals("high", android(sent.getValue(), "priority"));
        Object notification = android(sent.getValue(), "notification");
        assertEquals("ride_updates", ReflectionTestUtils.getField(notification, "channelId"));
    }

    @Test
    void aNonRidePushKeepsTheDefaultChannelAndStillHasTheInboxUrl() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/3");
        java.util.HashMap<String, String> data = new java.util.HashMap<>();
        data.put("type", "PROMO_ALERT");
        data.put("skipped", null);

        assertTrue(provider.sendDetailed(TOKEN, "title", "body", data).success());

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(sent.capture());
        assertEquals("/notifications", dataOf(sent.getValue()).get("url"));
        assertFalse(dataOf(sent.getValue()).containsKey("skipped"));
        assertEquals("high", android(sent.getValue(), "priority"));
        assertNull(ReflectionTestUtils.getField(android(sent.getValue(), "notification"), "channelId"));
    }

    @Test
    void theThreeArgumentSendStillSendsTheInboxUrl() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/4");

        assertTrue(provider.sendDetailed(TOKEN, "title", "body").success());

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(sent.capture());
        assertEquals(Map.of("url", "/notifications"), dataOf(sent.getValue()));
    }

    @Test
    void withoutFirebaseThePushIsNotConfiguredAndABlankTokenIsRefused() {
        FirebasePushProvider unconfigured = new FirebasePushProvider();
        assertEquals("NOT_CONFIGURED", unconfigured.sendDetailed(TOKEN, "t", "b", Map.of()).errorCode());
        assertEquals("RECIPIENT_BLANK", provider.sendDetailed(" ", "t", "b", Map.of()).errorCode());
    }

    @Test
    void aFailureWhileRemovingTheDeadTokenDoesNotBreakTheSend() throws Exception {
        FirebaseMessagingException failure = failing(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(failure);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(tokens).unregisterToken(TOKEN);

        assertFalse(provider.send(TOKEN, "title", "body"));
    }
}
