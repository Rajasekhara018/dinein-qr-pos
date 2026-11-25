package com.heuristiq.notification.adapters.out.channel.fcm.firebase;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.domain.repository.IDeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.heuristiq.notification.adapters.out.channel.ChannelAdapterConstants;
import com.heuristiq.notification.application.usecase.support.PushDataConstants;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.heuristiq.notification.adapters.out.channel.fcm.PushProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;

import static com.heuristiq.notification.application.port.out.ChannelProviderConstants.*;

@Component("firebasePushProvider")
public class FirebasePushProvider implements PushProvider {

    private static final Logger logger = LoggerFactory.getLogger(FirebasePushProvider.class);

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private NmsProperties props;

    @Autowired
    private IDeviceTokenRepository deviceTokenRepository;

    @Override
    public String getName() { return PROVIDER_FCM; }

    @Override
    public boolean send(String token, String title, String body) {
        return sendDetailed(token, title, body).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String token, String title, String body) {
        return sendDetailed(token, title, body, Map.of());
    }

    /**
     * Sends the notification with its data map (push data contract: type, rideId ... so the apps can route it), always
     * with {@code url=/notifications} for Rider_Web's service worker. Android gets HIGH priority; a RIDE push uses the
     * {@code ride_updates} notification channel the apps create at startup.
     */
    @Override
    public ProviderSendResult sendDetailed(String token, String title, String body, Map<String, String> data) {
        if (!StringUtils.hasText(token)) {
            logger.warn("FirebasePushProvider: token is blank, skipping");
            return ProviderSendResult.failed(PROVIDER_FCM, CODE_RECIPIENT_BLANK, "Device token is blank");
        }
        if (firebaseMessaging == null) {
            logger.error("FirebasePushProvider: Firebase not initialised — check nms.firebase.credentials-path");
            return ProviderSendResult.failed(PROVIDER_FCM, CODE_NOT_CONFIGURED, "Firebase is not initialised");
        }
        logger.info("FirebasePushProvider.send: token={}...", maskToken(token));
        try {
            Message message = buildMessage(token, title, body, data);
            String messageId = firebaseMessaging.send(message);
            logger.info("FirebasePushProvider: sent messageId={}", messageId);
            return ProviderSendResult.ok(PROVIDER_FCM, messageId, STATUS_ACCEPTED);
        } catch (FirebaseMessagingException e) {
            if (isPermanentlyInvalid(e)) {
                // The app was uninstalled or the token rotated -- it can never be delivered to again.
                // Remove it (for every user) so later pushes stop retrying it, and keep the log quiet:
                // this is routine housekeeping, not an error worth a stack trace.
                logger.warn("FirebasePushProvider: token {} is no longer valid ({}), removing it",
                        maskToken(token), e.getMessagingErrorCode());
                try {
                    deviceTokenRepository.unregisterToken(token);
                } catch (Exception cleanupFailure) {
                    logger.warn("FirebasePushProvider: could not remove dead token {}: {}",
                            maskToken(token), cleanupFailure.getMessage());
                }
            } else {
                logger.error("FirebasePushProvider.send failed: {}", e.getMessage(), e);
            }
            String code = e.getMessagingErrorCode() == null ? ProviderSendResult.CODE_EXCEPTION
                    : e.getMessagingErrorCode().name();
            return ProviderSendResult.failed(PROVIDER_FCM, code, e.getMessage());
        } catch (Exception e) {
            logger.error("FirebasePushProvider.send failed: {}", e.getMessage(), e);
            return ProviderSendResult.failed(PROVIDER_FCM, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * The FCM message: notification, data map (the url key is always the inbox, read by Rider_Web's
     * firebase-messaging-sw.js click handler), Android HIGH priority and, for a ride, the ride_updates channel.
     * Null keys or values are skipped (FCM rejects them).
     */
    Message buildMessage(String token, String title, String body, Map<String, String> data) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((key, value) -> {
                if (key != null && value != null) {
                    payload.put(key, value);
                }
            });
        }
        payload.put(PushDataConstants.KEY_URL, PushDataConstants.URL_NOTIFICATIONS);
        boolean ride = PushDataConstants.ENTITY_TYPE_RIDE.equalsIgnoreCase(payload.get(PushDataConstants.KEY_ENTITY_TYPE));
        AndroidNotification.Builder androidNotification = AndroidNotification.builder();
        if (ride) {
            androidNotification.setChannelId(ChannelAdapterConstants.FCM_RIDE_CHANNEL_ID);
        }
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(StringUtils.hasText(title) ? title : props.getBranding().getName())
                        .setBody(body != null ? body : "")
                        .build())
                .putAllData(payload)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(androidNotification.build())
                        .build())
                .build();
    }

    /**
     * UNREGISTERED = uninstalled/rotated token; SENDER_ID_MISMATCH = token from another Firebase
     * project. Neither can succeed again. INVALID_ARGUMENT is deliberately NOT included: it can also
     * mean a bad payload, which would wrongly wipe good tokens.
     */
    static boolean isPermanentlyInvalid(FirebaseMessagingException failure) {
        if (failure == null) return false;
        MessagingErrorCode code = failure.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH;
    }

    private String maskToken(String token) {
        return token.length() > 8 ? token.substring(0, 4) + "****" + token.substring(token.length() - 4) : "****";
    }
}
