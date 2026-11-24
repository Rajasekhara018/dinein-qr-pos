package com.heuristiq.audit.application;

import static com.heuristiq.audit.application.IngestConstants.*;
import com.heuristiq.audit.config.AuditProperties;
import com.heuristiq.audit.domain.AppChannel;
import com.heuristiq.audit.domain.AuditEvent;
import com.heuristiq.audit.domain.AuditEventType;
import com.heuristiq.audit.domain.AuditOutcome;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates, bounds, cleans and masks one submitted event. This is the single choke point that guarantees the
 * store never holds secrets or unbounded text: control characters are stripped (log and CSV injection), query
 * strings are cut from paths, keys that look like credentials are dropped from {@code details}, values that look
 * like tokens are redacted, recipients are masked, and the client IP is masked on request.
 */
@Component
public class EventSanitizer {

    private final AuditProperties.Ingest ingest;

    /**
     * Creates the sanitizer.
     *
     * @param properties service settings
     */
    public EventSanitizer(AuditProperties properties) {
        this.ingest = properties.getIngest();
    }

    /** Result of {@link #sanitize}: exactly one of event and error is set. */
    public record Outcome(AuditEvent event, String error) {
        static Outcome ok(AuditEvent e) {
            return new Outcome(e, null);
        }

        static Outcome rejected(String reason) {
            return new Outcome(null, reason);
        }
    }

    /**
     * Cleans one submitted event.
     *
     * @param in  the submitted event
     * @param now the receive time
     * @return the storable event (without {@code expireAt}) or the reason it was rejected
     */
    public Outcome sanitize(AuditEventInput in, Instant now) {
        if (in == null) {
            return Outcome.rejected("event is null");
        }
        AuditEventType type = parseEnum(AuditEventType.class, in.eventType());
        if (type == null) {
            return Outcome.rejected("eventType is required and must be one of ACCESS, SCREEN_VIEW, COMMUNICATION, PAYMENT_GATEWAY, DOMAIN");
        }
        String eventId = clean(in.eventId(), MAX_EVENT_ID);
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        } else if (!EVENT_ID.matcher(eventId).matches()) {
            return Outcome.rejected("eventId must be 8-64 characters of A-Z a-z 0-9 _ -");
        }
        String sourceService = clean(in.sourceService(), MAX_SERVICE_NAME);
        if (sourceService == null || !SERVICE_NAME.matcher(sourceService).matches()) {
            return Outcome.rejected("sourceService is required (A-Z a-z 0-9 _ . -, max 64)");
        }
        String screenKey = in.screenKey() != null && in.screenKey().trim().length() > MAX_SCREEN_KEY
                ? "x ".repeat(2) : clean(in.screenKey(), MAX_SCREEN_KEY);
        if (screenKey != null && !SCREEN_KEY.matcher(screenKey).matches()) {
            return Outcome.rejected("screenKey must be lower-case dotted segments, e.g. payments.refunds");
        }
        if (type == AuditEventType.SCREEN_VIEW && screenKey == null) {
            return Outcome.rejected("screenKey is required for SCREEN_VIEW");
        }
        String method = clean(in.method(), MAX_METHOD);
        if (method != null) {
            method = method.toUpperCase(Locale.ROOT);
            if (!METHODS.contains(method)) {
                return Outcome.rejected("method is not an HTTP method");
            }
        }
        Integer status = in.statusCode();
        if (status != null && (status < 0 || status > MAX_STATUS_CODE)) {
            return Outcome.rejected("statusCode must be between 0 and 999");
        }
        AuditOutcome outcome = parseEnum(AuditOutcome.class, in.outcome());
        if (in.outcome() != null && !in.outcome().isBlank() && outcome == null) {
            return Outcome.rejected("outcome must be SUCCESS, FAILURE or DENIED");
        }
        if (outcome == null) {
            if (status == null || status == 0) {
                if (type == AuditEventType.ACCESS) {
                    return Outcome.rejected("outcome or statusCode is required for ACCESS");
                }
            } else {
                outcome = AuditOutcome.fromStatus(status);
            }
        }
        AppChannel channel = parseEnum(AppChannel.class, in.appChannel());
        if (in.appChannel() != null && !in.appChannel().isBlank() && channel == null) {
            return Outcome.rejected("appChannel must be WEB, ADMIN_WEB, WALLET_WEB, CUSTOMER_APP, DRIVER_APP, RESTAURANT_APP or SERVICE");
        }
        String subjectType = upperSnake(in.subjectType(), MAX_SUBJECT_TYPE);
        if (subjectType == INVALID) {
            return Outcome.rejected("subjectType must be UPPER_SNAKE (A-Z 0-9 _), max " + MAX_SUBJECT_TYPE + " characters");
        }
        String subjectId = subjectId(in.subjectId());
        if (subjectId == INVALID) {
            return Outcome.rejected("subjectId must be 1-" + MAX_SUBJECT_ID + " characters of A-Z a-z 0-9 _ . : -");
        }
        String reasonCode = upperSnake(in.reasonCode(), MAX_REASON_CODE);
        if (reasonCode == INVALID) {
            return Outcome.rejected("reasonCode must be UPPER_SNAKE (A-Z 0-9 _), max " + MAX_REASON_CODE + " characters");
        }
        Long duration = in.durationMs();
        if (duration != null && (duration < 0 || duration > MAX_DURATION_MS)) {
            duration = null;
        }
        List<String> roles = roles(in.roles(), in.role());
        String role = clean(in.role(), MAX_ROLE);
        if (role != null) {
            role = stripRolePrefix(role);
        } else if (!roles.isEmpty()) {
            role = roles.get(0);
        }
        String userType = clean(in.userType(), MAX_USER_TYPE);
        userType = userType == null ? null : userType.toUpperCase(Locale.ROOT);
        if (userType == null || !USER_TYPES.contains(userType)) {
            userType = UserTypes.infer(roles, in.userId() != null);
        }
        Instant occurred = in.occurredAt() == null ? now : in.occurredAt();
        if (occurred.isAfter(now.plusSeconds(ingest.getMaxFutureSkewSeconds()))) {
            occurred = now;
        }
        String correlationId = id(in.correlationId());
        String requestId = id(in.requestId());

        AuditEvent event = AuditEvent.builder()
                .eventId(eventId).eventType(type).occurredAt(occurred).receivedAt(now)
                .sourceService(sourceService)
                .userId(clean(in.userId(), MAX_USER_ID)).userType(userType).role(role).roles(roles)
                .privilegeIds(privileges(in.privilegeIds()))
                .feature(clean(in.feature(), MAX_FEATURE)).module(clean(in.module(), MAX_MODULE)).screenKey(screenKey)
                .method(method).path(path(in.path())).statusCode(status).outcome(outcome).durationMs(duration)
                .correlationId(correlationId).requestId(requestId)
                .ipAddress(maskIp(clean(in.ipAddress(), MAX_IP_ADDRESS)))
                .userAgent(clean(in.userAgent(), MAX_USER_AGENT)).appChannel(channel).tenantId(clean(in.tenantId(), MAX_TENANT_ID))
                .subjectType(subjectType).subjectId(subjectId).reasonCode(reasonCode)
                .details(details(in.details()))
                .build();
        return Outcome.ok(event);
    }

    /** Marker (compared by identity) for a value that is present but malformed. */
    private static final String INVALID = new String("invalid");

    /**
     * Normalises an UPPER_SNAKE code: trimmed and upper-cased; null when blank; {@link #INVALID} when too long or
     * containing anything but A-Z 0-9 _ (so control characters and query syntax never get through).
     */
    static String upperSnake(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return v.length() > max || !UPPER_SNAKE_CODE.matcher(v).matches() ? INVALID : v;
    }

    /** Validates a subject id: null when blank, {@link #INVALID} when too long or not identifier characters. */
    static String subjectId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        return v.length() > MAX_SUBJECT_ID || !SUBJECT_ID.matcher(v).matches() ? INVALID : v;
    }

    private static String id(String raw) {
        String v = clean(raw, MAX_TRACE_ID);
        return v != null && TOKEN_LIKE_ID.matcher(v).matches() ? v : null;
    }

    /** Removes control characters, trims and bounds a string; blank becomes null. */
    static String clean(String raw, int max) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), max));
        for (int i = 0; i < raw.length() && sb.length() < max; i++) {
            char c = raw.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        String v = sb.toString().trim();
        return v.isEmpty() ? null : v;
    }

    /** Keeps the path only: cuts any query string or fragment so tokens in URLs are never stored. */
    static String path(String raw) {
        String v = clean(raw, MAX_RAW_PATH);
        if (v == null) {
            return null;
        }
        int cut = v.length();
        int q = v.indexOf('?');
        int h = v.indexOf('#');
        if (q >= 0) {
            cut = q;
        }
        if (h >= 0) {
            cut = Math.min(cut, h);
        }
        v = v.substring(0, cut);
        return v.length() > MAX_PATH ? v.substring(0, MAX_PATH) : (v.isEmpty() ? null : v);
    }

    private static String stripRolePrefix(String role) {
        return role.startsWith(ROLE_PREFIX) ? role.substring(ROLE_PREFIX.length()) : role;
    }

    private static List<String> roles(List<String> given, String primary) {
        List<String> out = new ArrayList<>();
        if (given != null) {
            for (String r : given) {
                String v = clean(r, MAX_ROLE);
                if (v != null) {
                    v = stripRolePrefix(v);
                    if (!out.contains(v) && out.size() < MAX_ROLES) {
                        out.add(v);
                    }
                }
            }
        }
        String p = clean(primary, MAX_ROLE);
        if (p != null && out.isEmpty()) {
            out.add(stripRolePrefix(p));
        }
        return out;
    }

    private static List<Integer> privileges(List<Integer> given) {
        if (given == null) {
            return List.of();
        }
        return given.stream().filter(i -> i != null && i >= 0).distinct().limit(MAX_PRIVILEGES).toList();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        String v = clean(raw, MAX_ENUM_NAME);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return true when the detail key names a credential, OTP, card number etc. and must not be stored */
    static boolean isSensitiveKey(String key) {
        String[] parts = NON_ALNUM.split(CAMEL_BOUNDARY.matcher(key).replaceAll("-"));
        StringBuilder joined = new StringBuilder();
        for (String p : parts) {
            String w = p.toLowerCase(Locale.ROOT);
            joined.append(w);
            if (SENSITIVE_WORDS.contains(w)) {
                return true;
            }
        }
        return joined.indexOf("apikey") >= 0 || joined.indexOf("accesskey") >= 0 || joined.indexOf("privatekey") >= 0;
    }

    private static boolean isMaskedKey(String key) {
        String words = CAMEL_BOUNDARY.matcher(key).replaceAll("-");
        for (String p : NON_ALNUM.split(words)) {
            if (MASKED_WORDS.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> details(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (out.size() >= MAX_DETAILS) {
                break;
            }
            String key = clean(e.getKey(), MAX_DETAIL_KEY);
            Object value = e.getValue();
            if (key == null || !DETAIL_KEY.matcher(key).matches() || isSensitiveKey(key)
                    || !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                continue;
            }
            String text = clean(String.valueOf(value), MAX_VALUE);
            if (text == null) {
                continue;
            }
            if (JWT_LIKE.matcher(text).find() || BEARER_LIKE.matcher(text).find()) {
                text = REDACTED_VALUE;
            } else if (isMaskedKey(key)) {
                text = maskRecipient(text);
            }
            out.put(key, text);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * Masks an email address, phone number or other recipient so it can be recognised but not read:
     * {@code j***@example.com}, {@code ******3210}. Values that already contain a mask are kept.
     *
     * @param value the raw recipient
     * @return the masked text
     */
    public static String maskRecipient(String value) {
        if (value == null || value.isEmpty() || value.contains("*")) {
            return value;
        }
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + RedactionConstants.MASK + value.substring(at);
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= RedactionConstants.MIN_MASKABLE_DIGITS) {
            return RedactionConstants.MASKED_DIGITS
                    + digits.substring(digits.length() - RedactionConstants.VISIBLE_DIGITS);
        }
        return value.charAt(0) + RedactionConstants.MASK;
    }

    private String maskIp(String ip) {
        if (ip == null || !ingest.isMaskIp()) {
            return ip;
        }
        if (IPV4.matcher(ip).matches()) {
            return ip.substring(0, ip.lastIndexOf('.')) + ".0";
        }
        if (ip.contains(":")) {
            try {
                byte[] bytes = InetAddress.getByName(ip).getAddress();
                if (bytes.length == IPV6_BYTES) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 8; i += 2) {
                        sb.append(String.format("%x", ((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
                    }
                    return sb + ":";
                }
            } catch (UnknownHostException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
