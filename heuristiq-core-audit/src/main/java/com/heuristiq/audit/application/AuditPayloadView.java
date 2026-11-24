package com.heuristiq.audit.application;

import com.heuristiq.audit.domain.AuditPayload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** Read model of a stored payload (see {@link AuditPayload}). Requires the payload privilege at the gateway. */
@Schema(description = "Redacted headers and bodies of one audit event. May contain personal data.")
public record AuditPayloadView(String eventId, Map<String, String> requestHeaders, Map<String, String> responseHeaders,
                               String requestBody, String responseBody, boolean requestBodyTruncated,
                               boolean responseBodyTruncated, Long requestBodySize, Long responseBodySize,
                               String bodyContentType) {

    /** Maps a stored payload to its read model. */
    public static AuditPayloadView from(AuditPayload p) {
        return new AuditPayloadView(p.getEventId(), p.getRequestHeaders(), p.getResponseHeaders(), p.getRequestBody(),
                p.getResponseBody(), p.isRequestBodyTruncated(), p.isResponseBodyTruncated(), p.getRequestBodySize(),
                p.getResponseBodySize(), p.getBodyContentType());
    }
}
