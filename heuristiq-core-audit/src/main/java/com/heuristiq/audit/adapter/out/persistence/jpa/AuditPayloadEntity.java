package com.heuristiq.audit.adapter.out.persistence.jpa;

import com.heuristiq.audit.domain.AuditPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** PostgreSQL row of an event's redacted headers and bodies (table {@code audit_event_payloads}, Flyway V3). */
@Getter
@Setter
@Entity
@Table(name = "audit_event_payloads")
public class AuditPayloadEntity {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", columnDefinition = "jsonb")
    private Map<String, String> requestHeaders;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "jsonb")
    private Map<String, String> responseHeaders;
    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;
    @Column(name = "request_body_truncated", nullable = false)
    private boolean requestBodyTruncated;
    @Column(name = "response_body_truncated", nullable = false)
    private boolean responseBodyTruncated;
    @Column(name = "request_body_size")
    private Long requestBodySize;
    @Column(name = "response_body_size")
    private Long responseBodySize;
    @Column(name = "body_content_type", length = 100)
    private String bodyContentType;
    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    /** @return the entity for a domain payload */
    public static AuditPayloadEntity from(AuditPayload p) {
        AuditPayloadEntity e = new AuditPayloadEntity();
        e.eventId = p.getEventId();
        e.requestHeaders = copy(p.getRequestHeaders());
        e.responseHeaders = copy(p.getResponseHeaders());
        e.requestBody = p.getRequestBody();
        e.responseBody = p.getResponseBody();
        e.requestBodyTruncated = p.isRequestBodyTruncated();
        e.responseBodyTruncated = p.isResponseBodyTruncated();
        e.requestBodySize = p.getRequestBodySize();
        e.responseBodySize = p.getResponseBodySize();
        e.bodyContentType = p.getBodyContentType();
        e.expireAt = p.getExpireAt();
        return e;
    }

    /** @return the domain payload */
    public AuditPayload toDomain() {
        return AuditPayload.builder().eventId(eventId).requestHeaders(copy(requestHeaders))
                .responseHeaders(copy(responseHeaders)).requestBody(requestBody).responseBody(responseBody)
                .requestBodyTruncated(requestBodyTruncated).responseBodyTruncated(responseBodyTruncated)
                .requestBodySize(requestBodySize).responseBodySize(responseBodySize).bodyContentType(bodyContentType)
                .expireAt(expireAt).build();
    }

    private static Map<String, String> copy(Map<String, String> m) {
        return m == null ? null : new LinkedHashMap<>(m);
    }
}
