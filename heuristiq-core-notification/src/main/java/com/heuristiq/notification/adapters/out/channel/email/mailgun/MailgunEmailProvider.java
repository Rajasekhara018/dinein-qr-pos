package com.heuristiq.notification.adapters.out.channel.email.mailgun;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.adapters.out.channel.email.EmailProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;

import lombok.RequiredArgsConstructor;

import static com.heuristiq.notification.application.port.out.ChannelProviderConstants.*;

@Component("mailgunEmailProvider")
@RequiredArgsConstructor
public class MailgunEmailProvider implements EmailProvider {

    private static final Logger logger = LoggerFactory.getLogger(MailgunEmailProvider.class);
    private static final java.util.regex.Pattern ID_FIELD = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private final NmsProperties props;
    private final WebClient webClient;
    private final Duration providerReadTimeout;

    @Override
    public String getName() { return PROVIDER_MAILGUN; }

    @Override
    public boolean send(String to, String subject, String htmlBody) {
        return sendDetailed(to, subject, htmlBody).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String subject, String htmlBody) {
        NmsProperties.Mailgun cfg = props.getMailgun();
        if (!StringUtils.hasText(cfg.getApiKey()) || !StringUtils.hasText(cfg.getDomain())) {
            logger.error("MailgunEmailProvider: api-key or domain not configured");
            return ProviderSendResult.failed(PROVIDER_MAILGUN, CODE_NOT_CONFIGURED, "Mailgun api-key or domain not configured");
        }
        logger.info("MailgunEmailProvider.send");
        try {
            String url = cfg.getApiUrl() + "/" + cfg.getDomain() + "/messages";

            // Basic auth: "api:<key>" base64
            String auth = "api:" + cfg.getApiKey();
            String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("from", StringUtils.hasText(cfg.getFrom()) ? cfg.getFrom() : props.getEmail().getFrom());
            form.add("to", to);
            form.add("subject", StringUtils.hasText(subject) ? subject : props.getBranding().defaultSubject());
            form.add("html", htmlBody != null ? htmlBody : "");

            String response = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(providerReadTimeout);
            return ProviderSendResult.ok(PROVIDER_MAILGUN, parseMessageId(response), STATUS_QUEUED);
        } catch (Exception e) {
            logger.error("MailgunEmailProvider.send failed: {}", e.getMessage(), e);
            return ProviderSendResult.failed(PROVIDER_MAILGUN, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * Reads {@code id} (for example {@code <2026...@mg.example.com>}) from the accepted response and strips
     * the angle brackets, which is how Mailgun's webhook reports the same id.
     */
    public static String parseMessageId(String response) {
        if (!StringUtils.hasText(response)) {
            return null;
        }
        java.util.regex.Matcher m = ID_FIELD.matcher(response);
        if (!m.find()) {
            return null;
        }
        String id = m.group(1).replace("<", "").replace(">", "").trim();
        return id.isEmpty() ? null : id;
    }
}
