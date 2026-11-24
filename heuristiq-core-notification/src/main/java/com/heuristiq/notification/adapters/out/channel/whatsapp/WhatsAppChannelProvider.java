package com.railcab.nms.adapters.out.channel.whatsapp;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.application.port.out.ChannelProvider;
import com.railcab.nms.application.port.out.ChannelProviderConstants;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.domain.model.NotificationChannel;

import lombok.RequiredArgsConstructor;

/**
 * WHATSAPP channel dispatcher.
 * Picks the active {@link WhatsAppProvider} at runtime based on
 * {@code nms.provider.whatsapp} (twilio | meta | digimile).
 */
@Component
@RequiredArgsConstructor
public class WhatsAppChannelProvider implements ChannelProvider {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppChannelProvider.class);

    private final NmsProperties props;
    private final List<WhatsAppProvider> providers;

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.WHATSAPP; }

    @Override
    public boolean send(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
        if (!StringUtils.hasText(recipient)) {
            logger.warn("WhatsAppChannelProvider: recipient blank, skipping");
            return ProviderSendResult.failed(null, ChannelProviderConstants.CODE_RECIPIENT_BLANK, ChannelProviderConstants.RECIPIENT_BLANK_MESSAGE);
        }
        String key = props.getProvider().getWhatsapp();
        WhatsAppProvider provider = resolve(key);
        if (provider == null) {
            logger.error("WhatsAppChannelProvider: no provider registered for key='{}'", key);
            return ProviderSendResult.failed(key, ChannelProviderConstants.CODE_NO_PROVIDER, "No WhatsApp provider registered for key " + key);
        }
        logger.info("WhatsAppChannelProvider: dispatching via provider={}", provider.getName());
        return provider.sendDetailed(recipient, body);
    }

    private WhatsAppProvider resolve(String key) {
        return providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }
}
