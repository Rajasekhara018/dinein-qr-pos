package com.heuristq.dinein.notification.push;

import java.util.Map;

/**
 * @param token device registration token of the provider
 * @param link  absolute URL opened when the notification is tapped; may be null
 * @param data  extra string key/values delivered to the app (e.g. {@code orderId})
 */
public record PushMessage(String token, String title, String body, String link, Map<String, String> data) {
}
