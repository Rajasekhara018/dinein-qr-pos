package com.heuristiq.notification.adapters.in.web.callback;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.application.dto.VoiceCallback;
import com.heuristiq.notification.application.port.in.CallbackAuthenticationUseCase;
import com.heuristiq.notification.application.port.in.VoiceCallbackUseCase;
import com.heuristiq.notification.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Exotel voice call reports: the call's StatusCallback (POST, form or multipart) and the IVR flow's Passthru applet (GET or
 * POST, with the pressed {@code digits}). Public at the gateway, so the caller proves itself with the callback shared
 * secret, either in {@code X-NMS-Callback-Secret} or as the password of HTTP basic credentials (Exotel cannot add a
 * header, so its callback URL carries {@code nms:<secret>@}). Refuses everything while the secret is not configured.
 *
 * <p>Answers 200 for every authenticated report except when the answer could not be published (503, so Exotel retries).
 * UNVERIFIED against a live Exotel account: parameter names follow Exotel's published callback documentation.
 */
@RestController
@RequestMapping(CallbackConstants.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Delivery receipts", description = "Provider callbacks that update the status of a delivery attempt")
public class VoiceCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceCallbackController.class);

    private final VoiceCallbackUseCase voiceCallbacks;
    private final CallbackAuthenticationUseCase authenticator;
    private final ObjectMapper objectMapper;

    @RequestMapping(value = CallbackConstants.PATH_VOICE_EXOTEL, method = {RequestMethod.POST, RequestMethod.GET})
    @Operation(summary = "Exotel voice call status and IVR digits",
            description = "Parameters CallSid, Status (completed, no-answer, busy, failed), digits, CustomField. Authenticated "
                    + "with X-NMS-Callback-Secret or basic credentials whose password is the voice callback secret (NMS_VOICE_CALLBACK_SECRET). Publishes "
                    + "the answer on Kafka topic nms-voice-responses.")
    public ResponseEntity<CommonResponseObject> exotel(
            @RequestHeader(value = CallbackConstants.HEADER_SHARED_SECRET, required = false) String secret,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> params) {
        if (!authenticator.voiceSecretValid(secret) && !authenticator.basicVoiceSecretValid(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CommonResponseObject.failure("Unauthorized"));
        }
        Map<String, String> lower = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        params.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                lower.put(name, values.get(0));
            }
        });
        JsonNode custom = parseCustomField(lower.get(CallbackConstants.EXOTEL_PARAM_CUSTOM_FIELD));
        VoiceCallback callback = new VoiceCallback(CallbackConstants.PROVIDER_EXOTEL,
                firstPresent(lower, CallbackConstants.EXOTEL_CALL_SID_PARAMS),
                firstPresent(lower, CallbackConstants.EXOTEL_STATUS_PARAMS),
                firstPresent(lower, CallbackConstants.EXOTEL_DIGITS_PARAMS),
                text(custom, CallbackConstants.CUSTOM_FIELD_ENTITY_TYPE),
                text(custom, CallbackConstants.CUSTOM_FIELD_ENTITY_ID),
                text(custom, CallbackConstants.CUSTOM_FIELD_EVENT_CODE));
        VoiceCallbackUseCase.Result result = voiceCallbacks.handle(callback);
        if (result == VoiceCallbackUseCase.Result.PUBLISH_FAILED) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(CommonResponseObject.failure("Voice response could not be published"));
        }
        return ResponseEntity.ok(CommonResponseObject.success("Processed", result));
    }

    /** CustomField is the JSON NMS put on the call; anything else (or nothing) gives null. */
    private JsonNode parseCustomField(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            logger.debug("Exotel CustomField is not JSON; ignored");
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstPresent(Map<String, String> params, List<String> names) {
        return CallbackParams.firstPresent(params, names);
    }
}
