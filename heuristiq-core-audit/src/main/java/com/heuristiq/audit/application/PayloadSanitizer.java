package com.railcab.audit.application;

import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditPayload;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Builds the stored payload of an event from what the producer sent, applying the service's own rules again even
 * though the gateway already redacted: headers and bodies are redacted by {@link PayloadRedactor}; bodies are
 * dropped (only content type and size kept) when the content type is not JSON, text or a form post, or when the
 * event path matches a sensitive-path pattern (login, OTP, password, token refresh, cards); bodies longer than the
 * cap are cut and flagged.
 */
@Component
public class PayloadSanitizer {

    private final AuditProperties.Payload settings;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * Creates the sanitizer.
     *
     * @param properties service settings
     */
    public PayloadSanitizer(AuditProperties properties) {
        this.settings = properties.getPayload();
    }

    /**
     * @param in    the submitted event
     * @param event the sanitized event (its path decides body skipping, its expireAt is reused)
     * @return the payload to store, or null when the producer sent no headers, bodies or sizes
     */
    public AuditPayload build(AuditEventInput in, AuditEvent event) {
        boolean hasAny = in.requestHeaders() != null || in.responseHeaders() != null || in.requestBody() != null
                || in.responseBody() != null || in.requestBodySize() != null || in.responseBodySize() != null;
        if (!hasAny) {
            return null;
        }
        String contentType = EventSanitizer.clean(in.bodyContentType(), IngestConstants.MAX_CONTENT_TYPE);
        boolean keepBodies = PayloadRedactor.isCapturableContentType(contentType) && !isSensitivePath(event.getPath());
        BodyResult request = body(keepBodies ? in.requestBody() : null, contentType, in.requestBodyTruncated());
        BodyResult response = body(keepBodies ? in.responseBody() : null, contentType, in.responseBodyTruncated());
        return AuditPayload.builder().eventId(event.getEventId())
                .requestHeaders(PayloadRedactor.redactHeaders(in.requestHeaders(), settings.getMaxHeaders()))
                .responseHeaders(PayloadRedactor.redactHeaders(in.responseHeaders(), settings.getMaxHeaders()))
                .requestBody(request.text()).responseBody(response.text())
                .requestBodyTruncated(request.truncated()).responseBodyTruncated(response.truncated())
                .requestBodySize(nonNegative(in.requestBodySize())).responseBodySize(nonNegative(in.responseBodySize()))
                .bodyContentType(contentType).expireAt(event.getExpireAt()).build();
    }

    /** @return true when bodies of this path are never stored */
    public boolean isSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        List<String> patterns = settings.getSkipBodyPaths();
        String p = path.toLowerCase(java.util.Locale.ROOT);
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern.toLowerCase(java.util.Locale.ROOT), p));
    }

    private record BodyResult(String text, boolean truncated) {
    }

    private BodyResult body(String raw, String contentType, Boolean producerTruncated) {
        if (raw == null || raw.isEmpty()) {
            return new BodyResult(null, false);
        }
        boolean truncated = Boolean.TRUE.equals(producerTruncated);
        String cleaned = stripControl(raw);
        if (cleaned.length() > settings.getMaxBodyBytes()) {
            cleaned = cleaned.substring(0, settings.getMaxBodyBytes());
            truncated = true;
        }
        String redacted = PayloadRedactor.redactBody(cleaned, contentType);
        if (redacted != null && redacted.length() > settings.getMaxBodyBytes()) {
            redacted = redacted.substring(0, settings.getMaxBodyBytes());
            truncated = true;
        }
        return new BodyResult(redacted, truncated);
    }

    private static Long nonNegative(Long v) {
        return v == null || v < 0 ? null : v;
    }

    private static String stripControl(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' ? ' ' : c);
        }
        return sb.toString();
    }
}
