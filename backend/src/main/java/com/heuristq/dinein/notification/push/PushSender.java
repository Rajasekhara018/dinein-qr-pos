package com.heuristq.dinein.notification.push;

import java.util.Map;

/**
 * Push provider SPI. To add a provider: implement this as a Spring bean with a unique {@link #provider()} code and
 * select it with {@code app.notifications.push.provider}. Subscriptions store the provider they were created for.
 */
public interface PushSender {

    /** Upper-case provider code, e.g. {@code FCM}. */
    String provider();

    /**
     * Sends synchronously; throws on failure.
     *
     * @throws InvalidPushTokenException when the provider reports the token as unregistered
     */
    void send(PushMessage message);

    /** Non-secret values the browser needs to obtain a token (e.g. FCM web config and VAPID key). */
    default Map<String, Object> publicClientConfig() {
        return Map.of();
    }
}
