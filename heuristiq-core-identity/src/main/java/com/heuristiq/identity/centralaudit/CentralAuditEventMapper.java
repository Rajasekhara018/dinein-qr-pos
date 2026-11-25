package com.heuristiq.identity.centralaudit;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.heuristiq.identity.centralaudit.CentralAuditConstants.*;

/**
 * Turns a UAM {@link AuditLog} into a central audit event. The audit service rejects an event whose subject type,
 * subject id or reason code is malformed, so those are normalised here (or left out) rather than risk losing the event.
 */
public final class CentralAuditEventMapper {

    private CentralAuditEventMapper() {
    }

    /**
     * Maps an entry.
     *
     * @param entry the UAM audit entry
     * @param now   event time
     * @return the event, never null
     */
    public static CentralAuditEvent toEvent(AuditLog entry, Instant now) {
        return new CentralAuditEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE_DOMAIN,
                now,
                SOURCE_SERVICE,
                blankToNull(entry.getUserId()),
                FEATURE,
                MODULE,
                entry.getStatus() == AuditStatus.FAILURE ? OUTCOME_FAILURE : OUTCOME_SUCCESS,
                blankToNull(entry.getCorrelationId()),
                blankToNull(entry.getRequestId()),
                blankToNull(entry.getIpAddress()),
                blankToNull(entry.getUserAgent()),
                blankToNull(entry.getTenantId()),
                upperSnake(entry.getEntityType(), MAX_SUBJECT_TYPE),
                subjectId(entry.getEntityId()),
                entry.getAction() == null ? null : upperSnake(entry.getAction().name(), MAX_REASON_CODE),
                details(entry));
    }

    /** Upper-snake form of a free name, or null when nothing usable is left. */
    static String upperSnake(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = NON_ALNUM.matcher(raw.trim()).replaceAll("_").toUpperCase(Locale.ROOT);
        cleaned = EDGE_UNDERSCORES.matcher(cleaned).replaceAll("");
        if (cleaned.length() > max) {
            cleaned = cleaned.substring(0, max);
        }
        return UPPER_SNAKE.matcher(cleaned).matches() ? cleaned : null;
    }

    static String subjectId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String id = raw.trim();
        return id.length() <= MAX_SUBJECT_ID && SUBJECT_ID.matcher(id).matches() ? id : null;
    }

    private static Map<String, Object> details(AuditLog entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, DETAIL_ACTION, entry.getAction() == null ? null : entry.getAction().name());
        put(out, DETAIL_STATUS, entry.getStatus() == null ? null : entry.getStatus().name());
        put(out, DETAIL_USERNAME, CentralAuditRedactor.text(entry.getUsername()));
        put(out, DETAIL_REMARKS, CentralAuditRedactor.text(entry.getRemarks()));
        put(out, DETAIL_DEVICE, entry.getDevice());
        flatten(out, DETAIL_BEFORE_PREFIX, entry.getOldValue());
        flatten(out, DETAIL_AFTER_PREFIX, entry.getNewValue());
        return out;
    }

    private static void flatten(Map<String, Object> out, String prefix, Map<String, Object> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = DETAIL_KEY_INVALID.matcher(prefix + e.getKey()).replaceAll("_");
            put(out, key.length() > MAX_DETAIL_KEY ? key.substring(0, MAX_DETAIL_KEY) : key, CentralAuditRedactor.redactValue(e.getKey(), String.valueOf(e.getValue())));
        }
    }

    private static void put(Map<String, Object> out, String key, String value) {
        if (!StringUtils.hasText(value) || out.size() >= MAX_DETAILS) {
            return;
        }
        out.put(key, value.length() > MAX_DETAIL_VALUE ? value.substring(0, MAX_DETAIL_VALUE) : value);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
