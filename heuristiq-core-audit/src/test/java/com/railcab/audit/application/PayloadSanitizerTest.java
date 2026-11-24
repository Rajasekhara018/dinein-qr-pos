package com.railcab.audit.application;

import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadSanitizerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private final AuditProperties props = new AuditProperties();
    private final EventSanitizer events = new EventSanitizer(props);
    private final PayloadSanitizer payloads = new PayloadSanitizer(props);

    private AuditPayload build(AuditEventInput in) {
        AuditEvent e = events.sanitize(in, NOW).event();
        e.setExpireAt(NOW.plusSeconds(86400));
        return payloads.build(in, e);
    }

    @Test
    void noHeadersOrBodiesMeansNoPayload() {
        assertThat(build(InputBuilder.access().build())).isNull();
    }

    @Test
    void storesRedactedHeadersAndBodiesWithEventKeyAndExpiry() {
        AuditPayload p = build(InputBuilder.access().eventId("evt-payload-1").method("POST")
                .requestHeaders(Map.of("Authorization", "Bearer x1234567890", "Accept", "*/*"))
                .requestBody("{\"name\":\"a\",\"password\":\"p\"}").responseBody("{\"token\":\"t\",\"id\":7}")
                .bodyContentType("application/json").build());
        assertThat(p.getEventId()).isEqualTo("evt-payload-1");
        assertThat(p.getExpireAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(p.getRequestHeaders()).containsEntry("Authorization", "***").containsEntry("Accept", "*/*");
        assertThat(p.getRequestBody()).contains("\"password\":\"***\"").doesNotContain("\"p\"");
        assertThat(p.getResponseBody()).contains("\"token\":\"***\"").contains("\"id\":7");
    }

    @Test
    void bodiesOverTheCapAreCutAndFlaggedTruncated() {
        props.getPayload().setMaxBodyBytes(50);
        AuditPayload p = build(InputBuilder.access().responseBody("{\"a\":\"" + "x".repeat(500) + "\"}")
                .bodyContentType("application/json").build());
        assertThat(p.getResponseBody()).hasSizeLessThanOrEqualTo(50);
        assertThat(p.isResponseBodyTruncated()).isTrue();
        assertThat(p.isRequestBodyTruncated()).isFalse();
    }

    @Test
    void producerTruncationFlagIsPreserved() {
        AuditPayload p = build(InputBuilder.access().requestBody("{\"a\":1").requestBodyTruncated(true)
                .bodyContentType("application/json").build());
        assertThat(p.isRequestBodyTruncated()).isTrue();
    }

    @Test
    void binaryAndMultipartContentTypesKeepOnlyHeadersTypeAndSize() {
        for (String type : new String[]{"multipart/form-data; boundary=x", "image/png", "application/pdf", "application/octet-stream"}) {
            AuditPayload p = build(new InputBuilderWithSize().type(type));
            assertThat(p.getRequestBody()).as(type).isNull();
            assertThat(p.getResponseBody()).isNull();
            assertThat(p.getBodyContentType()).isEqualTo(type);
            assertThat(p.getRequestBodySize()).isEqualTo(2048L);
            assertThat(p.getRequestHeaders()).containsEntry("Accept", "*/*");
        }
    }

    @Test
    void sensitivePathsNeverKeepBodies() {
        for (String path : new String[]{"/api/uam/identity/auth/login", "/api/v1/uam/identity/auth/refresh",
                "/api/rider/customer/otp/send", "/api/uam/users/{id}/change-password", "/api/uam/password/reset",
                "/api/rider/payment/cards", "/api/rider/payment/save-card/{id}", "/api/v1/auth/login",
                "/api/uam/token/refresh"}) {
            AuditPayload p = build(InputBuilder.access().method("POST").path(path)
                    .requestBody("{\"phone\":\"1\"}").responseBody("{\"ok\":true}").bodyContentType("application/json").build());
            assertThat(p.getRequestBody()).as(path).isNull();
            assertThat(p.getResponseBody()).as(path).isNull();
        }
        AuditPayload normal = build(InputBuilder.access().method("POST").path("/api/rider/config/config/settings/{id}")
                .requestBody("{\"v\":1}").bodyContentType("application/json").build());
        assertThat(normal.getRequestBody()).isEqualTo("{\"v\":1}");
    }

    @Test
    void sensitivePathListIsConfigurable() {
        props.getPayload().setSkipBodyPaths(java.util.List.of("/api/custom/**"));
        assertThat(payloads.isSensitivePath("/api/custom/x")).isTrue();
        assertThat(payloads.isSensitivePath("/api/uam/identity/auth/login")).isFalse();
    }

    /** Small helper: an ACCESS input with a body size but a non-text content type. */
    private static final class InputBuilderWithSize {
        AuditEventInput type(String contentType) {
            AuditEventInput base = InputBuilder.access().requestHeaders(Map.of("Accept", "*/*"))
                    .requestBody("BINARYDATA").responseBody("MOREBINARY").bodyContentType(contentType).build();
            return new AuditEventInput(base.eventId(), base.eventType(), base.occurredAt(), base.sourceService(),
                    base.userId(), base.userType(), base.role(), base.roles(), base.privilegeIds(), base.feature(),
                    base.module(), base.screenKey(), base.method(), base.path(), base.statusCode(), base.outcome(),
                    base.durationMs(), base.correlationId(), base.requestId(), base.ipAddress(), base.userAgent(),
                    base.appChannel(), base.tenantId(), base.subjectType(), base.subjectId(), base.reasonCode(), base.details(), base.requestHeaders(), base.responseHeaders(),
                    base.requestBody(), base.responseBody(), null, null, 2048L, 4096L, contentType);
        }
    }
}
