package com.heuristiq.gateway.filter.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

/**
 * Rewrites every backend's JSON response body into one canonical envelope
 * ({@code {success, message, data, errorCode}}) before it reaches the caller — the actual fix for
 * the cross-service response-envelope inconsistency this platform has: some services return
 * {@code ResponseObject{status, object, errorMsg}} (Location/Pricing/Dispatch/Ride/Payment), some
 * return plain unwrapped DTOs (CustomerService), and UAM returns yet a third shape
 * ({@code CommonResponseObject{success, message, responseObject, errorCode}}).
 *
 * Deliberately NOT wired into any live route yet — see {@link ResponseEnvelopeNormalizationFilter}'s
 * javadoc for why (neither mobile app currently routes through this gateway, so the filter itself
 * is gated behind {@code railcab.gateway.response-normalization.enabled}, default {@code false}).
 * This class holds the actual transformation logic in isolation so it's fully unit-testable
 * without any WebFlux/reactive plumbing.
 *
 * Detection is structural, not per-route config, so it works uniformly across every backend
 * without this gateway needing to know which service produced a given response:
 * <ul>
 *   <li>Has both {@code success} and {@code responseObject} keys -> UAM's CommonResponseObject shape.</li>
 *   <li>Has both {@code status} (boolean) and {@code object} keys -> the ResponseObject shape.</li>
 *   <li>Anything else (a plain DTO, an array, a primitive) -> wrapped as-is under {@code data},
 *       with {@code success} inferred from the HTTP status code.</li>
 * </ul>
 * A body that isn't valid JSON at all (e.g. actuator's plain-text health, Swagger's non-JSON
 * assets) is returned completely unchanged by the caller — this class only ever sees bodies the
 * caller has already confirmed are {@code application/json}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseEnvelopeNormalizer {

    private final ObjectMapper objectMapper;

    /**
     * @param rawJson    the exact bytes the backend returned (must be valid JSON — callers filter
     *                   by Content-Type before invoking this)
     * @param httpStatus the HTTP status Spring Cloud Gateway is about to send back to the client,
     *                   used only as the {@code success} fallback when the body has no status
     *                   field of its own (the plain-DTO case)
     * @return the canonical envelope, serialized back to bytes; on any parse failure, returns
     *         {@code rawJson} unchanged rather than risk corrupting a body this class doesn't
     *         understand
     */
    public byte[] normalize(byte[] rawJson, HttpStatusCode httpStatus) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            boolean http2xx = httpStatus != null && httpStatus.is2xxSuccessful();
            ObjectNode canonical = objectMapper.createObjectNode();

            if (root.isObject() && root.has("responseObject") && root.has("success")) {
                // UAM's CommonResponseObject{success, message, responseObject, errorCode, timestamp}
                canonical.put("success", root.path("success").asBoolean(http2xx));
                canonical.set("message", nonNullOrText(root.get("message"), null));
                canonical.set("data", nonNullOrNull(root.get("responseObject")));
                canonical.set("errorCode", nonNullOrNull(root.get("errorCode")));
            } else if (root.isObject() && root.has("status") && root.has("object")) {
                // ResponseObject{status, object, errorMsg} — Location/Pricing/Dispatch/Ride/Payment
                boolean success = root.path("status").asBoolean(http2xx);
                canonical.put("success", success);
                JsonNode errorMsg = root.get("errorMsg");
                canonical.set("message", errorMsg != null && !errorMsg.isNull()
                        ? errorMsg : TextNode.valueOf(success ? "Success" : "Error"));
                canonical.set("data", nonNullOrNull(root.get("object")));
                canonical.set("errorCode", NullNode.getInstance());
            } else {
                // A plain unwrapped DTO/array/primitive (CustomerService's style) — wrap as-is.
                canonical.put("success", http2xx);
                canonical.put("message", http2xx ? "Success" : "Error");
                canonical.set("data", root);
                canonical.set("errorCode", NullNode.getInstance());
            }

            return objectMapper.writeValueAsBytes(canonical);
        } catch (Exception e) {
            log.warn("Response-envelope normalization failed, passing body through unchanged: {}", e.getMessage());
            return rawJson;
        }
    }

    private JsonNode nonNullOrNull(JsonNode node) {
        return node != null ? node : NullNode.getInstance();
    }

    private JsonNode nonNullOrText(JsonNode node, String fallback) {
        if (node != null && !node.isNull()) return node;
        return fallback == null ? NullNode.getInstance() : TextNode.valueOf(fallback);
    }
}
