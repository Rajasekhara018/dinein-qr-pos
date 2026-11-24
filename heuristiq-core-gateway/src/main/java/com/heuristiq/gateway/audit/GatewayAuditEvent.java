package com.heuristiq.gateway.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * One ACCESS event in the wire format of Rider_AuditService's {@code POST /api/v1/audit/internal/events}.
 * Headers and bodies are raw until {@link AuditEventRedactor} runs on the batcher thread, just before sending;
 * an event never leaves the gateway unredacted.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayAuditEvent {
    private String eventId;
    private String eventType;
    private String occurredAt;
    private String sourceService;
    private String userId;
    /** The organization of the caller's session (the verified token's tenantId claim); null when there is none. */
    private String tenantId;
    private String userType;
    private String role;
    private List<String> roles;
    private List<Integer> privilegeIds;
    private String feature;
    private String module;
    private String method;
    private String path;
    private Integer statusCode;
    private String outcome;
    private Long durationMs;
    private String correlationId;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private String appChannel;
    private Map<String, Object> details;
    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;
    private String requestBody;
    private String responseBody;
    private Boolean requestBodyTruncated;
    private Boolean responseBodyTruncated;
    private Long requestBodySize;
    private Long responseBodySize;
    private String bodyContentType;
    /** Content type of each captured body, used by the redactor; not sent. */
    @JsonIgnore
    private String requestContentType;
    @JsonIgnore
    private String responseContentType;

    /** @return a rough in-memory size, used for the queue's byte budget */
    @JsonIgnore
    public long approximateBytes() {
        long n = 600;
        n += length(requestBody) + length(responseBody);
        n += headerBytes(requestHeaders) + headerBytes(responseHeaders);
        return n;
    }

    private static long length(String s) {
        return s == null ? 0 : (long) s.length() * 2;
    }

    private static long headerBytes(Map<String, String> h) {
        if (h == null) {
            return 0;
        }
        long n = 0;
        for (Map.Entry<String, String> e : h.entrySet()) {
            n += length(e.getKey()) + length(e.getValue()) + 32;
        }
        return n;
    }
}
