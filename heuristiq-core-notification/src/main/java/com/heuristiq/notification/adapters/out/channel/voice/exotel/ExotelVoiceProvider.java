package com.railcab.nms.adapters.out.channel.voice.exotel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.nms.adapters.out.channel.VendorPhoneFormat;
import com.railcab.nms.adapters.out.channel.voice.VoiceProvider;
import com.railcab.nms.application.port.out.ChannelProviderConstants;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.application.usecase.support.PushDataConstants;
import com.railcab.nms.application.usecase.support.RecipientMasker;
import com.railcab.nms.infrastructure.config.NmsProperties;

/**
 * Exotel voice provider: the v1 {@code Calls/connect} API calls the customer ({@code From}) from the ExoPhone
 * ({@code CallerId}) and hands the call to an IVR flow ({@code Url}) that plays the prompt and collects a digit. The call
 * carries a {@code CustomField} JSON with entityType, entityId, eventCode and messageId, and Exotel reports the outcome to
 * the {@code StatusCallback} URL ({@code /api/v1/nms/callbacks/voice/exotel}).
 *
 * <p>Credentials are sent as HTTP basic authentication (API key and token), never in the URL and never logged; phone
 * numbers are logged masked. Not retried by the resilience layer (a retry would ring the customer twice).
 *
 * <p>UNVERIFIED against a live Exotel account: parameter names and the JSON answer follow Exotel's published v1 API.
 */
@Component("exotelVoiceProvider")
public class ExotelVoiceProvider implements VoiceProvider {

    private static final Logger logger = LoggerFactory.getLogger(ExotelVoiceProvider.class);

    private final NmsProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * @param restClient client with the Exotel connect/read timeouts ({@code exotelRestClient} bean)
     */
    public ExotelVoiceProvider(NmsProperties props, @Qualifier("exotelRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return ChannelProviderConstants.PROVIDER_EXOTEL;
    }

    @Override
    public ProviderSendResult call(String toPhone, String script, Map<String, String> data) {
        NmsProperties.Voice.Exotel cfg = props.getVoice().getExotel();
        if (!configured(cfg)) {
            logger.error("ExotelVoiceProvider: Exotel is not configured (account sid, API key/token, subdomain, caller id or flow id missing)");
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_EXOTEL, ChannelProviderConstants.CODE_NOT_CONFIGURED,
                    "Exotel is not configured");
        }
        if (!StringUtils.hasText(toPhone)) {
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_EXOTEL, ChannelProviderConstants.CODE_RECIPIENT_BLANK,
                    ChannelProviderConstants.RECIPIENT_BLANK_MESSAGE);
        }
        String masked = RecipientMasker.mask(toPhone);
        logger.info("ExotelVoiceProvider: calling {}", masked);
        try {
            String response = restClient.post()
                    .uri(ExotelConstants.CONNECT_URL, cfg.getSubdomain().trim(), cfg.getAccountSid().trim())
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(cfg))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form(cfg, toPhone, data))
                    .retrieve()
                    .body(String.class);
            String[] call = readCall(response);
            if (call[0] == null) {
                logger.warn("ExotelVoiceProvider: Exotel accepted the call to {} but returned no call id", masked);
                return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_EXOTEL, ExotelConstants.CODE_NO_CALL_ID,
                        "Exotel returned no call id");
            }
            logger.info("ExotelVoiceProvider: call {} placed to {} status={}", call[0], masked, call[1]);
            return ProviderSendResult.ok(ChannelProviderConstants.PROVIDER_EXOTEL, call[0],
                    call[1] != null ? call[1] : ChannelProviderConstants.STATUS_QUEUED);
        } catch (RestClientResponseException e) {
            // The response body may echo request fields; keep only the HTTP status.
            logger.error("ExotelVoiceProvider: Exotel refused the call to {} with HTTP {}", masked, e.getStatusCode().value());
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_EXOTEL,
                    ExotelConstants.ERROR_CODE_PREFIX + e.getStatusCode().value(), "Exotel answered HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            logger.error("ExotelVoiceProvider: call to {} failed: {}", masked, e.getClass().getSimpleName());
            return ProviderSendResult.failed(ChannelProviderConstants.PROVIDER_EXOTEL, ProviderSendResult.CODE_EXCEPTION,
                    "Exotel call failed: " + e.getClass().getSimpleName());
        }
    }

    private static boolean configured(NmsProperties.Voice.Exotel cfg) {
        return StringUtils.hasText(cfg.getAccountSid()) && StringUtils.hasText(cfg.getApiKey())
                && StringUtils.hasText(cfg.getApiToken()) && StringUtils.hasText(cfg.getSubdomain())
                && StringUtils.hasText(cfg.getCallerId()) && StringUtils.hasText(cfg.getFlowId());
    }

    private static String basicAuth(NmsProperties.Voice.Exotel cfg) {
        String pair = cfg.getApiKey().trim() + ":" + cfg.getApiToken().trim();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private MultiValueMap<String, String> form(NmsProperties.Voice.Exotel cfg, String toPhone, Map<String, String> data) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(ExotelConstants.PARAM_FROM, VendorPhoneFormat.e164(toPhone));
        form.add(ExotelConstants.PARAM_CALLER_ID, cfg.getCallerId().trim());
        form.add(ExotelConstants.PARAM_URL, UriComponentsBuilder.fromUriString(ExotelConstants.FLOW_URL)
                .buildAndExpand(cfg.getAccountSid().trim(), cfg.getFlowId().trim()).toUriString());
        form.add(ExotelConstants.PARAM_CALL_TYPE, ExotelConstants.CALL_TYPE_TRANSACTIONAL);
        form.add(ExotelConstants.PARAM_TIME_LIMIT, String.valueOf(cfg.getTimeLimitSeconds()));
        form.add(ExotelConstants.PARAM_TIME_OUT, String.valueOf(cfg.getRingTimeoutSeconds()));
        String customField = customField(data);
        if (customField != null) {
            form.add(ExotelConstants.PARAM_CUSTOM_FIELD, customField);
        }
        String callback = ExotelCallbackUrl.withCredentials(cfg.getStatusCallbackUrl(), props.getCallbacks().getVoiceSharedSecret());
        if (callback != null) {
            form.add(ExotelConstants.PARAM_STATUS_CALLBACK, callback);
            form.add(ExotelConstants.PARAM_STATUS_CALLBACK_EVENT, ExotelConstants.STATUS_EVENT_TERMINAL);
        }
        return form;
    }

    /** {entityType, entityId, eventCode, messageId} as JSON; eventCode falls back to the event name. Null when empty. */
    String customField(Map<String, String> data) {
        Map<String, String> fields = new LinkedHashMap<>();
        putIfText(fields, PushDataConstants.KEY_ENTITY_TYPE, data.get(PushDataConstants.KEY_ENTITY_TYPE));
        putIfText(fields, PushDataConstants.KEY_ENTITY_ID, data.get(PushDataConstants.KEY_ENTITY_ID));
        String eventCode = data.get(PushDataConstants.KEY_EVENT_CODE);
        putIfText(fields, PushDataConstants.KEY_EVENT_CODE,
                StringUtils.hasText(eventCode) ? eventCode : data.get(PushDataConstants.KEY_TYPE));
        putIfText(fields, PushDataConstants.KEY_MESSAGE_ID, data.get(PushDataConstants.KEY_MESSAGE_ID));
        if (fields.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** [call sid, status] from {@code {"Call":{"Sid":..,"Status":..}}}; nulls when absent or unreadable. */
    private String[] readCall(String response) {
        if (!StringUtils.hasText(response)) {
            return new String[] {null, null};
        }
        try {
            JsonNode call = objectMapper.readTree(response).path(ExotelConstants.JSON_CALL);
            String sid = call.path(ExotelConstants.JSON_SID).asText(null);
            String status = call.path(ExotelConstants.JSON_STATUS).asText(null);
            return new String[] {StringUtils.hasText(sid) ? sid : null, status};
        } catch (JsonProcessingException e) {
            return new String[] {null, null};
        }
    }

    private static void putIfText(Map<String, String> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }
}
