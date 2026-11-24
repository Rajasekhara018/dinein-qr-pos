package com.heuristiq.gateway.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings of the access-audit publisher ({@code gateway.audit.*}). Auditing never blocks or fails a request:
 * events go to a bounded in-memory queue and are posted to Rider_AuditService in the background.
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.audit")
public class AuditProperties {

    /** Master switch. */
    private boolean enabled = true;
    /** Audit service base URL (e.g. http://audit:9106); empty = discover it through Eureka, or localhost:9106 without discovery. */
    private String baseUrl = "";
    /** Eureka service id used when {@link #baseUrl} is empty. */
    private String serviceId = "REALBOOM-AUDITSERVICE";
    /** Shared service credential sent as {@code X-Rider-Service-Token}. */
    private String serviceToken = "";
    /** Most events held in memory; when full the oldest is dropped. */
    private int queueCapacity = 5000;
    /** Approximate byte budget of the queue (bodies and headers included); the oldest are dropped above it. */
    private long maxQueueBytes = 64L * 1024 * 1024;
    /** Events per POST (the audit service accepts up to 500). */
    private int batchSize = 100;
    /** How often a partial batch is sent. */
    private long flushIntervalMs = 2000;
    /** Timeout of one POST. */
    private long timeoutMs = 3000;
    /** At most one warning about a failing audit service per this interval. */
    private long warnIntervalMs = 30_000;
    /** Take the client IP from the first {@code X-Forwarded-For} entry (the gateway sits behind a trusted proxy). */
    private boolean trustForwardedFor = true;
    /** Also store the query string, with the values of sensitive parameters replaced. Off by default (path only). */
    private boolean includeQuery = false;
    /** Replace id-like path segments (numbers, UUIDs, ObjectIds) with {@code {id}}. */
    private boolean normalisePath = true;
    /** Store request and response headers (credentials redacted). */
    private boolean captureHeaders = true;
    /** Store request and response bodies (JSON, text, form posts; redacted, capped). */
    private boolean captureBodies = true;
    /** Longest body kept per direction, in bytes of the (decoded) text; longer bodies are cut and flagged. */
    private int maxBodyBytes = 16_384;
    /** Ant patterns on the client path for which neither headers nor bodies are captured. */
    private List<String> captureExcludePaths = new ArrayList<>();
    /** Ant patterns on the client path whose bodies are never captured (login, OTP, password, token, cards). */
    private List<String> skipBodyPaths = new ArrayList<>(AuditConstants.DEFAULT_SKIP_BODY_PATHS);
    /** Ant patterns that are not audited at all (health, docs, static content). */
    private List<String> skipPaths = new ArrayList<>(List.of("/actuator", "/actuator/**", "/api/*/actuator/**",
            "/api/rider/*/actuator/**", "/fallback/**", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
            "/swagger-ui/**", "/api/*/v3/api-docs/**", "/api/*/swagger-ui/**", "/api/*/swagger-ui.html",
            "/api/rider/*/v3/api-docs/**", "/api/rider/*/swagger-ui/**", "/api/rider/*/swagger-ui.html",
            "/javadoc", "/javadoc/**", "/api/*/javadoc/**", "/api/rider/*/javadoc/**", "/favicon.ico",
            "/static/**", "/assets/**", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.ico", "/**/*.svg",
            "/api/v1/audit/screen-views"));
    /** Query parameter names (case-insensitive substrings) whose values are replaced when the query is stored. */
    private List<String> sensitiveQueryParams = new ArrayList<>(List.of("token", "secret", "key", "password", "pwd",
            "otp", "code", "signature", "sig", "auth", "session", "pin"));
}
