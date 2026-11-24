package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Builds the ACCESS event for one finished exchange. Pure: no I/O, no blocking. */
@Component
public class AuditEventFactory {

    private final AuditProperties properties;
    private final AuditPathRules rules;

    /**
     * Creates the factory.
     *
     * @param properties audit settings
     * @param rules      path rules
     */
    public AuditEventFactory(AuditProperties properties, AuditPathRules rules) {
        this.properties = properties;
        this.rules = rules;
    }

    /** Request/response capture results handed to {@link #build}; either may be null when capture is off. */
    public record Captured(AuditBodyCapture request, AuditBodyCapture response) {
    }

    /**
     * @param exchange   the exchange as the publisher saw it (identity headers set by the JWT filter)
     * @param status     response status, 0 when unknown
     * @param startedAt  when the request arrived
     * @param durationMs how long it took
     * @param captured   body captures, or null
     * @return the event (raw headers and bodies, redacted later by {@link AuditEventRedactor})
     */
    @SuppressWarnings("unchecked")
    public GatewayAuditEvent build(ServerWebExchange exchange, int status, Instant startedAt, long durationMs,
                                   Captured captured) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String path = rules.normalise(request.getURI().getPath());
        boolean captureAll = !rules.captureExcluded(request.getURI().getPath());

        List<String> roles = roles(headers);
        String role = firstNonBlank(headers.getFirst(GatewayConstants.X_USER_ROLE), roles.isEmpty() ? null : roles.get(0));
        String userId = headers.getFirst(GatewayConstants.X_USER_ID);
        String userType = userType(roles, userId != null);

        Set<Integer> privilegeIds = (Set<Integer>) exchange.getAttributes().get(AuditExchangeAttributes.PRIVILEGE_IDS);
        boolean denied = Boolean.TRUE.equals(exchange.getAttributes().get(AuditExchangeAttributes.DENIED));
        String outcome = denied || status == 401 || status == 403 ? OUTCOME_DENIED
                : (status >= 100 && status < 400 ? OUTCOME_SUCCESS : OUTCOME_FAILURE);

        Map<String, Object> details = new LinkedHashMap<>();
        if (denied) {
            details.put(DETAIL_DENY_REASON, DENY_REASON_PRIVILEGE);
        }
        if (properties.isIncludeQuery()) {
            String query = rules.sanitizeQuery(request.getURI().getRawQuery());
            if (query != null) {
                details.put(DETAIL_QUERY, query);
            }
        }

        GatewayAuditEvent.GatewayAuditEventBuilder b = GatewayAuditEvent.builder()
                .eventId(UUID.randomUUID().toString()).eventType(EVENT_TYPE_ACCESS).occurredAt(startedAt.toString())
                .sourceService(SOURCE).userId(userId).tenantId(blankToNull(headers.getFirst(GatewayConstants.X_TENANT_ID))).userType(userType).role(role).roles(roles)
                .privilegeIds(privilegeIds == null ? List.of() : new ArrayList<>(new TreeSet<>(privilegeIds)))
                .feature(blankToNull((String) exchange.getAttributes().get(AuditExchangeAttributes.SERVICE)))
                .module((String) exchange.getAttributes().get(AuditExchangeAttributes.MODULE))
                .method(request.getMethod() == null ? null : request.getMethod().name()).path(path)
                .statusCode(status == 0 ? null : status).outcome(outcome).durationMs(durationMs)
                .correlationId(headers.getFirst(HEADER_CORRELATION_ID)).requestId(request.getId())
                .ipAddress(clientIp(request)).userAgent(cut(headers.getFirst(HttpHeaders.USER_AGENT), 300))
                .appChannel(channel(headers, userType, role))
                .details(details.isEmpty() ? null : details);

        if (captured != null && captureAll) {
            if (properties.isCaptureHeaders()) {
                b.requestHeaders(flatten(headers));
                b.responseHeaders(flatten(exchange.getResponse().getHeaders()));
            }
            if (properties.isCaptureBodies()) {
                String reqType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
                String resType = exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (captured.request() != null) {
                    AuditBodyCapture.Snapshot s = captured.request().snapshot(reqType, headers.getFirst(HttpHeaders.CONTENT_ENCODING));
                    b.requestBody(s.text()).requestBodyTruncated(s.text() == null ? null : s.truncated())
                            .requestBodySize(s.size() == 0 ? null : s.size()).requestContentType(reqType);
                }
                if (captured.response() != null) {
                    AuditBodyCapture.Snapshot s = captured.response().snapshot(resType,
                            exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
                    b.responseBody(s.text()).responseBodyTruncated(s.text() == null ? null : s.truncated())
                            .responseBodySize(s.size() == 0 ? null : s.size()).responseContentType(resType);
                }
                b.bodyContentType(cut(firstNonBlank(resType, reqType), 100));
            }
        }
        return b.build();
    }

    private static Map<String, String> flatten(HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, values) -> out.put(name, String.join(", ", values)));
        return out;
    }

    private static List<String> roles(HttpHeaders headers) {
        String joined = headers.getFirst(GatewayConstants.X_USER_ROLES);
        if (!StringUtils.hasText(joined)) {
            String single = headers.getFirst(GatewayConstants.X_USER_ROLE);
            return StringUtils.hasText(single) ? List.of(single.trim()) : List.of();
        }
        List<String> out = new ArrayList<>();
        for (String r : joined.split(GatewayConstants.ROLE_LIST_DELIMITER)) {
            String v = r.trim();
            if (!v.isEmpty() && !out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    static String userType(List<String> roles, boolean hasUser) {
        for (String r : roles) {
            if (GatewayConstants.INTERNAL_SERVICE_ROLE.equalsIgnoreCase(r) || USER_TYPE_SERVICE.equalsIgnoreCase(r)) {
                return USER_TYPE_SERVICE;
            }
        }
        for (String r : roles) {
            if (GatewayConstants.CUSTOMER_ROLE.equalsIgnoreCase(r)) {
                return USER_TYPE_CUSTOMER;
            }
            if (GatewayConstants.DRIVER_ROLE.equalsIgnoreCase(r)) {
                return USER_TYPE_DRIVER;
            }
        }
        return roles.isEmpty() && !hasUser ? USER_TYPE_ANONYMOUS : USER_TYPE_STAFF;
    }

    private String clientIp(ServerHttpRequest request) {
        if (properties.isTrustForwardedFor()) {
            String xff = request.getHeaders().getFirst(HEADER_FORWARDED_FOR);
            if (StringUtils.hasText(xff)) {
                return cut(xff.split(",")[0].trim(), 64);
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
    }

    static String channel(HttpHeaders headers, String userType, String role) {
        String declared = headers.getFirst(APP_CHANNEL_HEADER);
        if (declared != null) {
            String d = declared.trim().toUpperCase(Locale.ROOT);
            if (CLIENT_CHANNELS.contains(d)) {
                return d;
            }
        }
        if (USER_TYPE_SERVICE.equals(userType)) {
            return CHANNEL_SERVICE;
        }
        String ua = headers.getFirst(HttpHeaders.USER_AGENT);
        if (ua == null) {
            return null;
        }
        String u = ua.toLowerCase(Locale.ROOT);
        boolean mobile = MOBILE_UA_TOKENS.stream().anyMatch(u::contains);
        if (mobile) {
            return USER_TYPE_DRIVER.equals(userType) ? CHANNEL_DRIVER_APP : USER_TYPE_CUSTOMER.equals(userType) ? CHANNEL_CUSTOMER_APP : null;
        }
        if (u.contains(BROWSER_UA_TOKEN)) {
            return USER_TYPE_STAFF.equals(userType) ? CHANNEL_ADMIN_WEB : CHANNEL_WEB;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
