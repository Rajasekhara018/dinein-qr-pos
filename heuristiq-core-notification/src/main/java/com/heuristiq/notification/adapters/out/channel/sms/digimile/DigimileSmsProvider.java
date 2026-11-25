package com.heuristiq.notification.adapters.out.channel.sms.digimile;

import com.heuristiq.notification.adapters.out.channel.VendorPhoneFormat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.adapters.out.channel.sms.SmsProvider;
import com.heuristiq.notification.adapters.out.channel.ChannelAdapterConstants;
import com.heuristiq.notification.application.port.out.ChannelProviderConstants;
import com.heuristiq.notification.application.port.out.ProviderSendResult;

import lombok.RequiredArgsConstructor;

/**
 * Digimile bulk-SMS gateway provider.
 * Response code 1701 = accepted by the gateway. {@code dlr=1} asks the gateway for a delivery report.
 *
 * <p>UNVERIFIED: the shape of the success body after the code ({@code 1701|<destination>:<messageId>})
 * is assumed from the gateway's usual format and has not been checked against a live Digimile
 * account; when no id can be read the attempt is still recorded, just without a provider message id
 * (so its delivery receipt cannot be matched).
 */
@Component("digimileSmsProvider")
@RequiredArgsConstructor
public class DigimileSmsProvider implements SmsProvider {

    private static final Logger logger = LoggerFactory.getLogger(DigimileSmsProvider.class);

    private final NmsProperties props;
    private final WebClient webClient;
    private final Duration providerReadTimeout;

    @Override
    public String getName() { return ChannelProviderConstants.PROVIDER_DIGIMILE; }

    @Override
    public boolean send(String to, String body, String templateId) {
        return sendDetailed(to, body, templateId).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String body, String templateId) {
        NmsProperties.Digimile cfg = props.getDigimile();
        if (!StringUtils.hasText(cfg.getUsername())) {
            logger.error("DigimileSmsProvider: username not configured");
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_DIGIMILE, ChannelProviderConstants.CODE_NOT_CONFIGURED, "Digimile username not configured");
        }
        logger.info("DigimileSmsProvider.send to a recipient ending {}", tail(to));
        try {
            String encodedMsg = URLEncoder.encode(body, StandardCharsets.UTF_8);
            String urlStr = cfg.getUrl()
                    + "username=" + cfg.getUsername()
                    + "&password=" + cfg.getPassword()
                    + "&type=0"
                    + "&dlr=1"
                    + "&destination=" + VendorPhoneFormat.digits(to)
                    + "&source=" + cfg.getSenderId()
                    + "&message=" + encodedMsg
                    + (StringUtils.hasText(cfg.getEntityId()) ? "&entityid=" + cfg.getEntityId() : "")
                    + (StringUtils.hasText(templateId) ? "&tempid=" + templateId : "");

            String responseStr = webClient.get()
                    .uri(urlStr)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(providerReadTimeout);
            responseStr = responseStr != null ? responseStr : "";
            logger.info("DigimileSmsProvider response code={}", responseCode(responseStr));

            String resCode = responseCode(responseStr);
            if (ChannelAdapterConstants.DIGIMILE_SUCCESS_CODE.equals(resCode)) {
                return ProviderSendResult.ok(ChannelProviderConstants.PROVIDER_DIGIMILE, parseMessageId(responseStr), resCode);
            }
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_DIGIMILE,
                    ChannelAdapterConstants.DIGIMILE_ERROR_CODE_PREFIX
                            + (resCode.isEmpty() ? ChannelAdapterConstants.DIGIMILE_NO_CODE : resCode),
                    "Digimile response code " + resCode);
        } catch (Exception e) {
            // The exception text can echo the request URL (which carries the gateway password); keep only its type.
            logger.error("DigimileSmsProvider.send failed for a recipient ending {}: {}", tail(to), e.getClass().getSimpleName());
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_DIGIMILE, ProviderSendResult.CODE_EXCEPTION,
                    "Digimile call failed: " + e.getClass().getSimpleName());
        }
    }

    static String responseCode(String response) {
        return response.contains("|") ? response.split("\\|")[0].trim() : response.trim();
    }

    /** Reads the id from {@code 1701|<destination>:<messageId>[,...]}; null when the body has no such part. */
    static String parseMessageId(String response) {
        int bar = response.indexOf('|');
        if (bar < 0 || bar == response.length() - 1) {
            return null;
        }
        String first = response.substring(bar + 1).split(",")[0].trim();
        int colon = first.lastIndexOf(':');
        String id = colon >= 0 ? first.substring(colon + 1).trim() : first;
        return id.isEmpty() ? null : id;
    }

    private static String tail(String value) {
        return value == null || value.length() <= 4 ? "****" : value.substring(value.length() - 4);
    }
}
