package com.railcab.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One event as a trusted producer (gateway, NMS, payment) submits it. Everything except {@code eventType} is
 * optional or derived; strings are bounded and cleaned by {@code EventSanitizer}. Headers and bodies may be
 * sent for ACCESS events; they are redacted again by {@code PayloadSanitizer} and stored apart from the event.
 * Keys in {@code details} that look like credentials are dropped.
 *
 * <p>{@code subjectType}, {@code subjectId} and {@code reasonCode} are optional first-class, indexed fields so any
 * service's events can be found by what they are about. DOMAIN events from DMS (feature {@code dms}, module
 * {@code DOCUMENTS}) use subjectType {@code DOCUMENT} and may carry the {@code details} keys documentType, category,
 * action, storageProvider, scanVerdict and sizeBytes.
 */
@Schema(description = "One audit event submitted by a trusted service.")
public record AuditEventInput(
        @Schema(description = "Producer-generated unique id (8-64 chars of A-Z a-z 0-9 _ -); resending the same id stores it once.",
                example = "6f1c2b0e-8a54-4d0e-9d1a-3c1f6a2b7e10") String eventId,
        @Schema(description = "ACCESS, SCREEN_VIEW, COMMUNICATION, PAYMENT_GATEWAY or DOMAIN.", example = "ACCESS") String eventType,
        @Schema(description = "When it happened (ISO-8601); defaults to the receive time.") Instant occurredAt,
        @Schema(description = "Producing service.", example = "api-gateway") String sourceService,
        @Schema(description = "Acting user id (UAM subject).") String userId,
        @Schema(description = "CUSTOMER, DRIVER, STAFF, SERVICE or ANONYMOUS; derived from the roles when absent.") String userType,
        @Schema(description = "Primary role.", example = "SUPER_ADMIN") String role,
        @Schema(description = "All roles of the caller.") List<String> roles,
        @Schema(description = "Privilege ids that were matched (or required, when denied).") List<Integer> privilegeIds,
        @Schema(description = "Feature, normally the catalog service slug.", example = "rider-config") String feature,
        @Schema(description = "Catalog module.", example = "CONFIGURATION") String module,
        @Schema(description = "Stable screen key, lower-case dotted.", example = "payments.refunds") String screenKey,
        @Schema(description = "HTTP method.", example = "GET") String method,
        @Schema(description = "Normalised path without query string.", example = "/api/rider/config/config/settings/{id}") String path,
        @Schema(description = "HTTP status code.", example = "200") Integer statusCode,
        @Schema(description = "SUCCESS, FAILURE or DENIED; derived from the status when absent.") String outcome,
        @Schema(description = "Duration in milliseconds.") Long durationMs,
        @Schema(description = "Correlation id of the request chain.") String correlationId,
        @Schema(description = "Request id.") String requestId,
        @Schema(description = "Client IP (the last octet is masked on storage).") String ipAddress,
        @Schema(description = "Client user agent.") String userAgent,
        @Schema(description = "WEB, ADMIN_WEB, WALLET_WEB, CUSTOMER_APP, DRIVER_APP, RESTAURANT_APP or SERVICE.") String appChannel,
        @Schema(description = "Tenant or brand code, when known.") String tenantId,
        @Schema(description = "What the event is about: kind of subject, UPPER_SNAKE, max 40 chars (A-Z 0-9 _; lower case is "
                + "upper-cased). Examples: DOCUMENT, NOTIFICATION_MESSAGE, PAYMENT. A malformed value rejects the event. "
                + "Not a secret and never redacted.", example = "DOCUMENT") String subjectType,
        @Schema(description = "Id of the subject, max 128 chars of A-Z a-z 0-9 _ . : - (ids only, no free text). A malformed "
                + "value rejects the event. Searchable together with subjectType.", example = "doc_01HZX") String subjectId,
        @Schema(description = "Stable machine-readable reason, UPPER_SNAKE, max 60 chars (lower case is upper-cased), e.g. "
                + "VIRUS_DETECTED, MIME_MISMATCH, PROVIDER_TIMEOUT. A malformed value rejects the event.",
                example = "VIRUS_DETECTED") String reasonCode,
        @Schema(description = "Up to 20 non-secret attributes (communication: channel, templateCode, originService, "
                + "recipient, provider, attempt, providerStatus, failureReason; payment: gateway, operation, "
                + "gatewayRef, paymentId, failureReason).") Map<String, Object> details,
        @Schema(description = "Request headers (credentials are redacted again here).") Map<String, String> requestHeaders,
        @Schema(description = "Response headers (credentials are redacted again here).") Map<String, String> responseHeaders,
        @Schema(description = "Request body text, already cut to the cap by the producer; redacted again here.") String requestBody,
        @Schema(description = "Response body text; redacted again here.") String responseBody,
        @Schema(description = "True when the request body was cut at the cap.") Boolean requestBodyTruncated,
        @Schema(description = "True when the response body was cut at the cap.") Boolean responseBodyTruncated,
        @Schema(description = "Original request body size in bytes.") Long requestBodySize,
        @Schema(description = "Original response body size in bytes.") Long responseBodySize,
        @Schema(description = "Content type of the captured bodies.") String bodyContentType) {
}
