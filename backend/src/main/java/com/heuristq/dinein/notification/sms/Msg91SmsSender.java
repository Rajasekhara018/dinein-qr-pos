package com.heuristq.dinein.notification.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.notification.DeliveryException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MSG91 Flow API: {@code POST /api/v5/flow} with the {@code authkey} header. MSG91 sends the pre-approved template
 * configured for the event; our rendered text is ignored and the message variables ({@code ##token##},
 * {@code ##restaurant##}, ...) are passed per recipient.
 */
@Component
public class Msg91SmsSender implements SmsSender {

    public static final String CODE = "MSG91";

    private final Msg91Properties props;
    private final RestClient restClient;

    public Msg91SmsSender(Msg91Properties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String provider() {
        return CODE;
    }

    @Override
    public void send(SmsMessage message) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("MSG91 is not configured");
        }
        String key = message.template().toLowerCase(Locale.ROOT).replace('_', '-');
        String templateId = props.templates() == null ? null : props.templates().get(key);
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalStateException("No MSG91 template configured for " + key);
        }
        Map<String, String> recipient = new LinkedHashMap<>(message.variables());
        recipient.put("mobiles", message.to().replace("+", ""));
        Map<String, Object> body = Map.of("template_id", templateId, "short_url", "0",
                "recipients", List.of(recipient));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(props.baseUrl() + "/api/v5/flow")
                    .header("authkey", props.authKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new DeliveryException("MSG91 HTTP " + e.getStatusCode().value());
        }
        // MSG91 reports some errors with HTTP 200 and {"type":"error"}.
        if (response != null && "error".equalsIgnoreCase(response.path("type").asText())) {
            throw new DeliveryException("MSG91 error: " + response.path("message").asText("unknown"));
        }
    }
}
