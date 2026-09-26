package com.heuristq.dinein.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param serviceAccountPath path to the Firebase service-account JSON (secret; mount it, never commit it)
 * @param projectId          optional; defaults to the project in the service-account file
 * @param vapidKey           public web-push key from Firebase console (sent to browsers)
 * @param webConfig          public Firebase web app config as JSON (apiKey, projectId, messagingSenderId, appId)
 */
@ConfigurationProperties(prefix = "app.notifications.push.fcm")
public record FcmProperties(String serviceAccountPath, String projectId, String vapidKey, String webConfig) {
}
