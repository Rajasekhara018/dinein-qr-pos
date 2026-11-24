package com.heuristiq.notification.adapters.out.channel.sms;

import java.util.List;

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
 * SMS channel dispatcher.
 * Picks the active {@link SmsProvider} at runtime based on
 * {@code nms.provider.sms} (twilio | digimile).
 */
@Component
@RequiredArgsConstructor
public class SmsChannelProvider implements ChannelProvider {

    private static final Logger logger = LoggerFactory.getLogger(SmsChannelProvider.class);

    private final NmsProperties props;
    private final List<SmsProvider> providers;

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.SMS; }

    @Override
    public boolean send(String recipient, String subject, String body, String templateId) {
        return sendDetailed(recipient, subject, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
        if (!StringUtils.hasText(recipient)) {
            logger.warn("SmsChannelProvider: recipient blank, skipping");
            return ProviderSendResult.failed(null, ChannelProviderConstants.CODE_RECIPIENT_BLANK, ChannelProviderConstants.RECIPIENT_BLANK_MESSAGE);
        }
        String key = props.getProvider().getSms();
        SmsProvider provider = resolve(key);
        if (provider == null) {
            logger.error("SmsChannelProvider: no provider registered for key='{}'", key);
            return ProviderSendResult.failed(key, ChannelProviderConstants.CODE_NO_PROVIDER, "No SMS provider registered for key " + key);
        }
        logger.info("SmsChannelProvider: dispatching via provider={}", provider.getName());
        return provider.sendDetailed(recipient, body, templateId);
    }

    private SmsProvider resolve(String key) {
        return providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }
}
