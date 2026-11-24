package com.railcab.nms.adapters.in.web.callback;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.nms.application.dto.DeliveryReceipt;
import com.railcab.nms.application.dto.ReceiptOutcome;
import com.railcab.nms.application.port.in.CallbackAuthenticationUseCase;
import com.railcab.nms.application.port.in.DeliveryReceiptUseCase;
import com.railcab.nms.domain.model.DeliveryStatus;
import com.railcab.nms.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Inbound provider delivery receipts. Public at the gateway (providers carry no user token), so
 * every endpoint proves the caller itself: a shared secret header, or the provider's own signature.
 * An endpoint whose secret is not configured refuses everything.
 *
 * <p>Once authenticated, every call is answered 200 whatever happened to the receipt (applied,
 * duplicate, unknown message, status ignored): providers retry on non-2xx, and none of these cases
 * can be fixed by retrying. Receipts are idempotent, see {@link DeliveryReceiptUseCase}.
 *
 * <p>Verification status: the Twilio, Meta and Mailgun payload and signature formats follow each
 * vendor's published webhook documentation; the Digimile DLR parameter names are an assumption and
 * the generic endpoint is provider-agnostic. None has been exercised against a live provider.
 */
@RestController
@RequestMapping(CallbackConstants.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Delivery receipts", description = "Provider callbacks that update the status of a delivery attempt")
public class DeliveryReceiptController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryReceiptController.class);

    private final DeliveryReceiptUseCase receipts;
    private final CallbackAuthenticationUseCase authenticator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Request body of the provider-agnostic endpoint. */
    public record GenericReceiptRequest(String provider, String providerMessageId, String status,
            String providerStatus, String errorCode, String errorMessage, LocalDateTime occurredAt) {
    }

    @RequestMapping(value = CallbackConstants.PATH_DIGIMILE_SMS, method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Digimile SMS delivery report (UNVERIFIED parameter names)",
            description = "Query or form parameters: msgid, status, err. Requires header X-NMS-Callback-Secret.")
    public ResponseEntity<CommonResponseObject> digimile(
            @RequestHeader(value = CallbackConstants.HEADER_SHARED_SECRET, required = false) String secret,
            @RequestParam Map<String, String> params) {
        if (!authenticator.sharedSecretValid(secret)) {
            return unauthorized();
        }
        Map<String, String> lower = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        lower.putAll(params);
        String id = firstPresent(lower, CallbackConstants.DIGIMILE_ID_PARAMS);
        String rawStatus = firstPresent(lower, CallbackConstants.DIGIMILE_STATUS_PARAMS);
        String error = firstPresent(lower, CallbackConstants.DIGIMILE_ERROR_PARAMS);
        Optional<DeliveryStatus> status = ReceiptStatusMapper.fromDigimile(rawStatus);
        return respond(status.map(s -> new DeliveryReceipt(CallbackConstants.PROVIDER_DIGIMILE, id, s, rawStatus,
                error, error, null)).orElse(null), CallbackConstants.PROVIDER_DIGIMILE);
    }

    @PostMapping(CallbackConstants.PATH_GENERIC)
    @Operation(summary = "Provider-agnostic delivery receipt",
            description = "JSON with providerMessageId and an NMS status (QUEUED, SENT, DELIVERED, FAILED, BOUNCED, READ). "
                    + "Requires header X-NMS-Callback-Secret.")
    public ResponseEntity<CommonResponseObject> generic(
            @RequestHeader(value = CallbackConstants.HEADER_SHARED_SECRET, required = false) String secret,
            @RequestBody GenericReceiptRequest body) {
        if (!authenticator.sharedSecretValid(secret)) {
            return unauthorized();
        }
        Optional<DeliveryStatus> status = ReceiptStatusMapper.fromName(body.status());
        return respond(status.map(s -> new DeliveryReceipt(body.provider(), body.providerMessageId(), s,
                body.providerStatus(), body.errorCode(), body.errorMessage(), body.occurredAt())).orElse(null),
                body.provider());
    }

    @PostMapping(value = CallbackConstants.PATH_TWILIO, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Twilio message status callback (SMS and WhatsApp)",
            description = "Form body per Twilio's StatusCallback. Verified with X-Twilio-Signature.")
    public ResponseEntity<CommonResponseObject> twilio(
            @RequestHeader(value = CallbackConstants.HEADER_TWILIO_SIGNATURE, required = false) String signature,
            @RequestParam MultiValueMap<String, String> params) {
        if (!authenticator.twilioSignatureValid(params, signature)) {
            return unauthorized();
        }
        String rawStatus = params.getFirst(CallbackConstants.TWILIO_PARAM_STATUS);
        Optional<DeliveryStatus> status = ReceiptStatusMapper.fromTwilio(rawStatus);
        return respond(status.map(s -> new DeliveryReceipt(CallbackConstants.PROVIDER_TWILIO,
                params.getFirst(CallbackConstants.TWILIO_PARAM_SID), s, rawStatus,
                params.getFirst(CallbackConstants.TWILIO_PARAM_ERROR_CODE),
                params.getFirst(CallbackConstants.TWILIO_PARAM_ERROR_MESSAGE), null)).orElse(null),
                CallbackConstants.PROVIDER_TWILIO);
    }

    @GetMapping(value = CallbackConstants.PATH_META_WHATSAPP, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Meta WhatsApp webhook subscription handshake")
    public ResponseEntity<String> metaVerify(
            @RequestParam(name = CallbackConstants.META_PARAM_MODE, required = false) String mode,
            @RequestParam(name = CallbackConstants.META_PARAM_VERIFY_TOKEN, required = false) String token,
            @RequestParam(name = CallbackConstants.META_PARAM_CHALLENGE, required = false) String challenge) {
        if (CallbackConstants.META_MODE_SUBSCRIBE.equals(mode) && authenticator.metaVerifyTokenValid(token)
                && StringUtils.hasText(challenge)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(CallbackConstants.PATH_META_WHATSAPP)
    @Operation(summary = "Meta WhatsApp Cloud API status webhook",
            description = "Verified with X-Hub-Signature-256 (HMAC-SHA256 of the raw body with the app secret).")
    public ResponseEntity<CommonResponseObject> meta(
            @RequestHeader(value = CallbackConstants.HEADER_META_SIGNATURE, required = false) String signature,
            @RequestBody byte[] rawBody) {
        if (!authenticator.metaSignatureValid(rawBody, signature)) {
            return unauthorized();
        }
        JsonNode root = parse(rawBody);
        if (root == null) {
            return ResponseEntity.badRequest().body(CommonResponseObject.failure("Unreadable body"));
        }
        int applied = 0;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode st : change.path("value").path("statuses")) {
                    String rawStatus = st.path("status").asText(null);
                    JsonNode error = st.path("errors").path(0);
                    Optional<DeliveryStatus> status = ReceiptStatusMapper.fromMeta(rawStatus);
                    if (status.isPresent() && receipts.apply(new DeliveryReceipt(CallbackConstants.PROVIDER_META,
                            st.path("id").asText(null), status.get(), rawStatus,
                            error.path("code").asText(null), error.path("title").asText(null),
                            epochSeconds(st.path("timestamp").asText(null)))) == ReceiptOutcome.APPLIED) {
                        applied++;
                    }
                }
            }
        }
        return ResponseEntity.ok(CommonResponseObject.success("Processed", Map.of("applied", applied)));
    }

    @PostMapping(CallbackConstants.PATH_MAILGUN)
    @Operation(summary = "Mailgun event webhook",
            description = "Verified with the signature block (HMAC-SHA256 of timestamp+token with the signing key).")
    public ResponseEntity<CommonResponseObject> mailgun(@RequestBody byte[] rawBody) {
        JsonNode root = parse(rawBody);
        if (root == null) {
            return ResponseEntity.badRequest().body(CommonResponseObject.failure("Unreadable body"));
        }
        JsonNode sig = root.path("signature");
        if (!authenticator.mailgunSignatureValid(sig.path("timestamp").asText(null), sig.path("token").asText(null),
                sig.path("signature").asText(null))) {
            return unauthorized();
        }
        JsonNode event = root.path("event-data");
        String rawEvent = event.path("event").asText(null);
        Optional<DeliveryStatus> status = ReceiptStatusMapper.fromMailgun(rawEvent, event.path("severity").asText(null));
        String id = event.path("message").path("headers").path("message-id").asText(null);
        JsonNode delivery = event.path("delivery-status");
        return respond(status.map(s -> new DeliveryReceipt(CallbackConstants.PROVIDER_MAILGUN, id, s, rawEvent,
                delivery.path("code").asText(null), delivery.path("message").asText(null),
                epochSeconds(event.path("timestamp").asText(null)))).orElse(null),
                CallbackConstants.PROVIDER_MAILGUN);
    }

    private ResponseEntity<CommonResponseObject> respond(DeliveryReceipt receipt, String provider) {
        if (receipt == null) {
            logger.info("Delivery callback from {} carried a status NMS does not track; ignored", provider);
            return ResponseEntity.ok(CommonResponseObject.success("Ignored", ReceiptOutcome.INVALID));
        }
        ReceiptOutcome outcome = receipts.apply(receipt);
        return ResponseEntity.ok(CommonResponseObject.success("Processed", outcome));
    }

    private static ResponseEntity<CommonResponseObject> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(CommonResponseObject.failure("Unauthorized"));
    }

    private JsonNode parse(byte[] raw) {
        try {
            return objectMapper.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime epochSeconds(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long seconds = (long) Double.parseDouble(value);
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), clock.getZone());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstPresent(Map<String, String> params, List<String> names) {
        return CallbackParams.firstPresent(params, names);
    }
}
