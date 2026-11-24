package com.heuristiq.notification.adapters.out.channel.voice;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.application.port.out.ChannelProviderConstants;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import lombok.RequiredArgsConstructor;

/**
 * VOICE channel dispatcher. Picks the active {@link VoiceProvider} from {@code nms.provider.voice}; with {@code none} (the
 * default) the channel is off and every attempt is recorded as {@code NOT_CONFIGURED} without calling anyone.
 */
@Component
@RequiredArgsConstructor
public class VoiceChannelProvider implements ChannelProvider {

    private static final Logger logger = LoggerFactory.getLogger(VoiceChannelProvider.class);

    private final NmsProperties props;
    private final List<VoiceProvider> providers;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.VOICE;
    }

    @Override
    public boolean send(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId, Map.of());
    }

    /** Places the call; {@code data} supplies the entity and message ids the call carries for its status callback. */
    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId,
            Map<String, String> data) {
        if (!StringUtils.hasText(recipient)) {
            logger.warn("VoiceChannelProvider: phone blank, skipping");
            return ProviderSendResult.failed(null, ChannelProviderConstants.CODE_RECIPIENT_BLANK,
                    ChannelProviderConstants.RECIPIENT_BLANK_MESSAGE);
        }
        String key = props.getProvider().getVoice();
        if (!StringUtils.hasText(key) || ChannelProviderConstants.PROVIDER_NONE.equalsIgnoreCase(key.trim())) {
            logger.info("VoiceChannelProvider: voice channel is off (nms.provider.voice=none), call not placed");
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_NONE, ChannelProviderConstants.CODE_NOT_CONFIGURED,
                    "Voice channel is off (nms.provider.voice=none)");
        }
        VoiceProvider provider = providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(key.trim()))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            logger.error("VoiceChannelProvider: no provider registered for key='{}'", key);
            return ProviderSendResult.failed(key, ChannelProviderConstants.CODE_NO_PROVIDER, "No voice provider registered for key " + key);
        }
        return provider.call(recipient, body, data == null ? Map.of() : data);
    }
}
