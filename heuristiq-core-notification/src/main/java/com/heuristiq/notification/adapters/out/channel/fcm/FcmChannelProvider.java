package com.heuristiq.notification.adapters.out.channel.fcm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.application.port.out.ChannelProviderConstants;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.domain.model.NotificationChannel;

import lombok.RequiredArgsConstructor;

/**
 * FCM_PUSH channel dispatcher.
 * Picks the active {@link PushProvider} at runtime based on
 * {@code nms.provider.push} (fcm).
 */
@Component
@RequiredArgsConstructor
public class FcmChannelProvider implements ChannelProvider {

    private static final Logger logger = LoggerFactory.getLogger(FcmChannelProvider.class);

    private final NmsProperties props;
    private final List<PushProvider> providers;

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.FCM_PUSH; }

    @Override
    public boolean send(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId, Map.of());
    }

    /** Sends the push with its data map (type, rideId ...), so the apps can route it. */
    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId,
            Map<String, String> data) {
        if (!StringUtils.hasText(recipient)) {
            logger.warn("FcmChannelProvider: token blank, skipping");
            return ProviderSendResult.failed(null, ChannelProviderConstants.CODE_RECIPIENT_BLANK, ChannelProviderConstants.RECIPIENT_BLANK_MESSAGE);
        }
        String key = props.getProvider().getPush();
        PushProvider provider = resolve(key);
        if (provider == null) {
            logger.error("FcmChannelProvider: no provider registered for key='{}'", key);
            return ProviderSendResult.failed(key, ChannelProviderConstants.CODE_NO_PROVIDER, "No push provider registered for key " + key);
        }
        logger.info("FcmChannelProvider: dispatching via provider={}", provider.getName());
        return provider.sendDetailed(recipient, subject, body, data == null ? Map.of() : data);
    }

    private PushProvider resolve(String key) {
        return providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }
}
