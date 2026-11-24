package com.heuristiq.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * The redacted request/response headers and bodies of one event, stored apart from it (table/collection
 * {@code audit_event_payloads}, keyed by the event's {@code eventId}, same {@code expireAt}) so event lists stay
 * small and fast. May still contain personal data such as names or addresses: only credentials, card and account
 * numbers are removed and phones, emails and UPI addresses are masked.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditPayload {
    private String eventId;
    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;
    private String requestBody;
    private String responseBody;
    private boolean requestBodyTruncated;
    private boolean responseBodyTruncated;
    /** Original size in bytes before any cut. */
    private Long requestBodySize;
    private Long responseBodySize;
    private String bodyContentType;
    private Instant expireAt;
}
