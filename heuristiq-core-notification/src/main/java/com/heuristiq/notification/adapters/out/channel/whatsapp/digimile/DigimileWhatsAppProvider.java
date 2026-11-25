package com.heuristiq.notification.adapters.out.channel.whatsapp.digimile;

import com.heuristiq.notification.adapters.out.channel.VendorPhoneFormat;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.adapters.out.channel.whatsapp.WhatsAppProvider;

import lombok.RequiredArgsConstructor;

import static com.heuristiq.notification.application.port.out.ChannelProviderConstants.*;

@Component("digimileWhatsAppProvider")
@RequiredArgsConstructor
public class DigimileWhatsAppProvider implements WhatsAppProvider {

    private static final Logger logger = LoggerFactory.getLogger(DigimileWhatsAppProvider.class);

    private final NmsProperties props;
    private final WebClient webClient;
    private final Duration providerReadTimeout;

    @Override
    public String getName() { return PROVIDER_DIGIMILE; }

    @Override
    public boolean send(String to, String body) {
        NmsProperties.DigimileWhatsApp cfg = props.getDigimileWhatsapp();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            logger.error("DigimileWhatsAppProvider: api-key not configured");
            return false;
        }
        logger.info("DigimileWhatsAppProvider.send: to={}", to);
        try {
            Map<String, Object> requestBody = Map.of(
                    "ApiKey", cfg.getApiKey(),
                    "SenderID", cfg.getSenderId(),
                    "MobileNo", VendorPhoneFormat.digits(to),
                    "Message", body != null ? body : ""
            );
            webClient.post()
                    .uri(cfg.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .block(providerReadTimeout);
            return true;
        } catch (Exception e) {
            logger.error("DigimileWhatsAppProvider.send failed for {}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
