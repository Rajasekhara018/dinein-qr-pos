package com.heuristiq.notification.adapters.out.channel.whatsapp.meta;

import com.heuristiq.notification.adapters.out.channel.VendorPhoneFormat;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.adapters.out.channel.whatsapp.WhatsAppProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;

import lombok.RequiredArgsConstructor;

import static com.heuristiq.notification.application.port.out.ChannelProviderConstants.*;

@Component("metaWhatsAppProvider")
@RequiredArgsConstructor
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private static final Logger logger = LoggerFactory.getLogger(MetaWhatsAppProvider.class);

    private final NmsProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final Duration providerReadTimeout;

    @Override
    public String getName() { return PROVIDER_META; }

    @Override
    public boolean send(String to, String body) {
        return sendDetailed(to, body).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String body) {
        NmsProperties.Meta.WhatsApp cfg = props.getMeta().getWhatsapp();
        if (!StringUtils.hasText(cfg.getToken())) {
            logger.error("MetaWhatsAppProvider: token not configured");
            return ProviderSendResult.failed(PROVIDER_META, CODE_NOT_CONFIGURED, "Meta WhatsApp token not configured");
        }
        logger.info("MetaWhatsAppProvider.send to a recipient ending {}", tail(to));
        try {
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("body", body);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("messaging_product", "whatsapp");
            root.put("to", VendorPhoneFormat.digits(to));
            root.put("type", "text");
            root.set("text", textNode);

            String payload = objectMapper.writeValueAsString(root);
            String url = cfg.getApiUrl().replace("{phone_number_id}", cfg.getPhoneNumberId());

            String response = webClient.post()
                    .uri(url)
                    .headers(headers -> headers.setBearerAuth(cfg.getToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(providerReadTimeout);
            return ProviderSendResult.ok(PROVIDER_META, parseMessageId(response), STATUS_ACCEPTED);
        } catch (Exception e) {
            logger.error("MetaWhatsAppProvider.send failed for a recipient ending {}: {}", tail(to), e.getMessage(), e);
            return ProviderSendResult.failed(PROVIDER_META, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    /** Reads {@code messages[0].id} (the wamid) from the Cloud API response; null when absent or unreadable. */
    public String parseMessageId(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        try {
            JsonNode id = objectMapper.readTree(response).path("messages").path(0).path("id");
            return id.isTextual() && StringUtils.hasText(id.asText()) ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String tail(String value) {
        return value == null || value.length() <= 4 ? "****" : value.substring(value.length() - 4);
    }
}
