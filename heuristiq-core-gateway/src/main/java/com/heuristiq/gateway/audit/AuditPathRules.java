package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;

import com.heuristiq.gateway.filter.InternalPathGuard;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Path decisions of the audit publisher: what is not audited, what is not captured, how a path and a query string
 * are stored, and which paths belong to the audit service's internal endpoints.
 */
@Component
public class AuditPathRules {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final AuditProperties properties;
    private final LowercasedPatterns skipPaths;
    private final LowercasedPatterns captureExcludePaths;
    private final LowercasedPatterns skipBodyPaths;

    /**
     * Creates the rules.
     *
     * @param properties audit settings
     */
    public AuditPathRules(AuditProperties properties) {
        this.properties = properties;
        this.skipPaths = new LowercasedPatterns(properties::getSkipPaths);
        this.captureExcludePaths = new LowercasedPatterns(properties::getCaptureExcludePaths);
        this.skipBodyPaths = new LowercasedPatterns(properties::getSkipBodyPaths);
    }

    /** @return true when the request must not be audited at all (OPTIONS, health, docs, static, screen-view posts) */
    public boolean skip(String path, String method) {
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(method)) {
            return true;
        }
        return matchesAny(skipPaths, path);
    }

    /** @return true when neither headers nor bodies of this path are captured */
    public boolean captureExcluded(String path) {
        return matchesAny(captureExcludePaths, path);
    }

    /** @return true when bodies of this path are never captured (login, OTP, password, token, card paths) */
    public boolean bodySkipped(String path) {
        return matchesAny(skipBodyPaths, path);
    }

    private boolean matchesAny(LowercasedPatterns patterns, String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        for (String pattern : patterns.get()) {
            if (matcher.match(pattern, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param path the request path as the client sent it
     * @return the path with id-like segments (numbers, UUIDs, ObjectIds) replaced by {@code {id}}, when enabled
     */
    public String normalise(String path) {
        if (path == null || !properties.isNormalisePath()) {
            return path;
        }
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (ID_SEGMENT.matcher(segments[i]).matches()) {
                segments[i] = "{id}";
            }
        }
        String joined = String.join("/", segments);
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    /**
     * @param rawQuery the raw query string (may be null)
     * @return the query with the values of sensitive parameter names replaced by {@code ***}, keeping names and order
     */
    public String sanitizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if (eq < 0) {
                sb.append(name);
            } else if (isSensitiveParam(UriUtils.decode(name, StandardCharsets.UTF_8))) {
                sb.append(name).append("=***");
            } else {
                sb.append(pair);
            }
        }
        String q = sb.toString();
        return q.length() > 500 ? q.substring(0, 500) : q;
    }

    private boolean isSensitiveParam(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String s : properties.getSensitiveQueryParams()) {
            if (n.contains(s.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the audit service's internal endpoints in any of the client path shapes (with or without a version
     * segment, any case, repeated slashes, percent-encoding), so the gateway can refuse to route them.
     *
     * @param rawPath the request path
     * @return true for {@code .../audit/internal/...}
     */
    public static boolean isInternalAuditPath(String rawPath) {
        return InternalPathGuard.isInternalPath(rawPath, INTERNAL_AUDIT);
    }

    /**
     * Route filter that answers 404 for the audit service's internal ingest endpoints, in any path spelling,
     * before the request is routed. The service also demands the service token; this keeps the endpoint off the
     * public surface entirely.
     *
     * @return the filter
     */
    public static GatewayFilter blockInternalAuditFilter() {
        return InternalPathGuard.blockInternalPaths(INTERNAL_AUDIT);
    }
}
