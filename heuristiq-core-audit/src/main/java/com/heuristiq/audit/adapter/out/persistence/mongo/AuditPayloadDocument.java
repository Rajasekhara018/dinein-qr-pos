package com.heuristiq.audit.adapter.out.persistence.mongo;

import static com.heuristiq.audit.adapter.out.persistence.PersistenceConstants.*;
import com.heuristiq.audit.domain.AuditPayload;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MongoDB representation of an event's redacted headers and bodies (collection {@code audit_event_payloads}).
 * The {@code _id} is the event's {@code eventId}; {@code expireAt} has a TTL index so a payload leaves with its event.
 * Header names are stored as given: names with dots are unsafe as Mongo field names, so the maps are stored with
 * dots replaced by {@code U+FF0E} on the way in and restored on the way out.
 */
@Document(collection = PAYLOADS_COLLECTION)
@Getter
@Setter
public class AuditPayloadDocument {

 
    @Id
    private String eventId;
    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;
    private String requestBody;
    private String responseBody;
    private boolean requestBodyTruncated;
    private boolean responseBodyTruncated;
    private Long requestBodySize;
    private Long responseBodySize;
    private String bodyContentType;
    @Indexed(name = "ttl_audit_payload_expire_at", expireAfter = "0s")
    private Instant expireAt;

    /** @return the document for a domain payload */
    public static AuditPayloadDocument from(AuditPayload p) {
        AuditPayloadDocument d = new AuditPayloadDocument();
        d.eventId = p.getEventId();
        d.requestHeaders = rename(p.getRequestHeaders(), ".", DOT_REPLACEMENT);
        d.responseHeaders = rename(p.getResponseHeaders(), ".", DOT_REPLACEMENT);
        d.requestBody = p.getRequestBody();
        d.responseBody = p.getResponseBody();
        d.requestBodyTruncated = p.isRequestBodyTruncated();
        d.responseBodyTruncated = p.isResponseBodyTruncated();
        d.requestBodySize = p.getRequestBodySize();
        d.responseBodySize = p.getResponseBodySize();
        d.bodyContentType = p.getBodyContentType();
        d.expireAt = p.getExpireAt();
        return d;
    }

    /** @return the domain payload */
    public AuditPayload toDomain() {
        return AuditPayload.builder().eventId(eventId).requestHeaders(rename(requestHeaders, DOT_REPLACEMENT, "."))
                .responseHeaders(rename(responseHeaders, DOT_REPLACEMENT, ".")).requestBody(requestBody).responseBody(responseBody)
                .requestBodyTruncated(requestBodyTruncated).responseBodyTruncated(responseBodyTruncated)
                .requestBodySize(requestBodySize).responseBodySize(responseBodySize).bodyContentType(bodyContentType)
                .expireAt(expireAt).build();
    }

    private static Map<String, String> rename(Map<String, String> m, String from, String to) {
        if (m == null) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k.replace(from, to), v));
        return out;
    }
}
