package com.railcab.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Test helper that builds an {@link AuditEventInput} with sensible defaults for an ACCESS event. */
public final class InputBuilder {

    String eventId;
    String eventType = "ACCESS";
    Instant occurredAt;
    String sourceService = "api-gateway";
    String userId = "u-1";
    String userType;
    String role = "SUPER_ADMIN";
    List<String> roles = List.of("SUPER_ADMIN");
    List<Integer> privilegeIds = List.of(100);
    String feature = "rider-config";
    String module = "CONFIGURATION";
    String screenKey;
    String method = "GET";
    String path = "/api/rider/config/config/settings";
    Integer statusCode = 200;
    String outcome;
    Long durationMs = 12L;
    String correlationId = "corr-1";
    String requestId;
    String ipAddress;
    String userAgent;
    String appChannel;
    String tenantId;
    String subjectType;
    String subjectId;
    String reasonCode;
    Map<String, Object> details;
    Map<String, String> requestHeaders;
    Map<String, String> responseHeaders;
    String requestBody;
    String responseBody;
    Boolean requestBodyTruncated;
    Boolean responseBodyTruncated;
    Long requestBodySize;
    Long responseBodySize;
    String bodyContentType;

    public static InputBuilder access() {
        return new InputBuilder();
    }

    public InputBuilder eventId(String v) {
        eventId = v;
        return this;
    }

    public InputBuilder eventType(String v) {
        eventType = v;
        return this;
    }

    public InputBuilder occurredAt(Instant v) {
        occurredAt = v;
        return this;
    }

    public InputBuilder sourceService(String v) {
        sourceService = v;
        return this;
    }

    public InputBuilder userId(String v) {
        userId = v;
        return this;
    }

    public InputBuilder role(String v) {
        role = v;
        return this;
    }

    public InputBuilder roles(List<String> v) {
        roles = v;
        return this;
    }

    public InputBuilder privilegeIds(List<Integer> v) {
        privilegeIds = v;
        return this;
    }

    public InputBuilder feature(String v) {
        feature = v;
        return this;
    }

    public InputBuilder module(String v) {
        module = v;
        return this;
    }

    public InputBuilder screenKey(String v) {
        screenKey = v;
        return this;
    }

    public InputBuilder method(String v) {
        method = v;
        return this;
    }

    public InputBuilder path(String v) {
        path = v;
        return this;
    }

    public InputBuilder statusCode(Integer v) {
        statusCode = v;
        return this;
    }

    public InputBuilder outcome(String v) {
        outcome = v;
        return this;
    }

    public InputBuilder tenantId(String v) {
        tenantId = v;
        return this;
    }

    public InputBuilder subjectType(String v) {
        subjectType = v;
        return this;
    }

    public InputBuilder subjectId(String v) {
        subjectId = v;
        return this;
    }

    public InputBuilder reasonCode(String v) {
        reasonCode = v;
        return this;
    }

    public InputBuilder correlationId(String v) {
        correlationId = v;
        return this;
    }

    public InputBuilder ipAddress(String v) {
        ipAddress = v;
        return this;
    }

    public InputBuilder appChannel(String v) {
        appChannel = v;
        return this;
    }

    public InputBuilder details(Map<String, Object> v) {
        details = v;
        return this;
    }

    public InputBuilder requestHeaders(Map<String, String> v) {
        requestHeaders = v;
        return this;
    }

    public InputBuilder responseHeaders(Map<String, String> v) {
        responseHeaders = v;
        return this;
    }

    public InputBuilder requestBody(String v) {
        requestBody = v;
        return this;
    }

    public InputBuilder responseBody(String v) {
        responseBody = v;
        return this;
    }

    public InputBuilder bodyContentType(String v) {
        bodyContentType = v;
        return this;
    }

    public InputBuilder requestBodyTruncated(Boolean v) {
        requestBodyTruncated = v;
        return this;
    }

    public AuditEventInput build() {
        return new AuditEventInput(eventId, eventType, occurredAt, sourceService, userId, userType, role, roles,
                privilegeIds, feature, module, screenKey, method, path, statusCode, outcome, durationMs, correlationId,
                requestId, ipAddress, userAgent, appChannel, tenantId, subjectType, subjectId, reasonCode, details, requestHeaders, responseHeaders,
                requestBody, responseBody, requestBodyTruncated, responseBodyTruncated, requestBodySize,
                responseBodySize, bodyContentType);
    }
}
